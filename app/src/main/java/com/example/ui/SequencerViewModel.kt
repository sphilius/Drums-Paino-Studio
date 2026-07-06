package com.example.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioDecoder
import com.example.audio.AudioEngine
import com.example.audio.DrumSound
import com.example.audio.Waveform
import com.example.database.ProjectDatabase
import com.example.database.ProjectEntity
import com.example.database.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SequencerViewModel(private val repository: ProjectRepository) : ViewModel() {

    private val TAG = "SequencerViewModel"

    // Unified list of saved projects
    val savedProjects: StateFlow<List<ProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently loaded project ID
    private val _currentProjectId = MutableStateFlow<Int?>(null)
    val currentProjectId = _currentProjectId.asStateFlow()

    private val _currentProjectName = MutableStateFlow("Untitled Project")
    val currentProjectName = _currentProjectName.asStateFlow()

    // Active visual tab
    private val _activeTab = MutableStateFlow(0) // 0: Pads, 1: Drum Sequencer, 2: Piano Roll, 3: Vocal Recorder, 4: FX, 5: Mixer, 6: Cloud Sync
    val activeTab = _activeTab.asStateFlow()

    // Real-time parameters
    private val _bpm = MutableStateFlow(120)
    val bpm = _bpm.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep = _currentStep.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isRecordingSeq = MutableStateFlow(false)
    val isRecordingSeq = _isRecordingSeq.asStateFlow()

    // Waveform of Synth roll
    private val _synthWave = MutableStateFlow(Waveform.SAWTOOTH)
    val synthWave = _synthWave.asStateFlow()

    // Export WAV state
    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus = _exportStatus.asStateFlow()

    // Cloud simulation states
    private val _bluetoothSyncStatus = MutableStateFlow("Disconnected")
    val bluetoothSyncStatus = _bluetoothSyncStatus.asStateFlow()

    private val _usbSyncStatus = MutableStateFlow("Disconnected")
    val usbSyncStatus = _usbSyncStatus.asStateFlow()

    private val _cloudSyncing = MutableStateFlow(false)
    val cloudSyncing = _cloudSyncing.asStateFlow()

    private val _activeCollaborators = MutableStateFlow<List<String>>(emptyList())
    val activeCollaborators = _activeCollaborators.asStateFlow()

    private val _vstStatus = MutableStateFlow("Vessel FX Modules Active")
    val vstStatus = _vstStatus.asStateFlow()

    init {
        // Collect engine state periodically or listen
        viewModelScope.launch {
            AudioEngine.playbackState.collect {
                _isPlaying.value = it
            }
        }
        viewModelScope.launch {
            AudioEngine.stepFlow.collect {
                _currentStep.value = it
            }
        }
    }

    fun selectTab(tab: Int) {
        _activeTab.value = tab
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            AudioEngine.stopEngine()
            _isRecordingSeq.value = false
            AudioEngine.isRecordingSeq = false
        } else {
            AudioEngine.startEngine()
        }
    }

    fun toggleRecording() {
        _isRecordingSeq.value = !_isRecordingSeq.value
        AudioEngine.isRecordingSeq = _isRecordingSeq.value
        if (_isRecordingSeq.value && !_isPlaying.value) {
            AudioEngine.startEngine()
        }
    }

    fun updateBpm(newBpm: Int) {
        _bpm.value = newBpm
        AudioEngine.bpm = newBpm
    }

    fun updateBarCount(count: Int) {
        AudioEngine.currentTrackBarCount = count
    }

    fun getBarCount() = AudioEngine.currentTrackBarCount

    fun changeSynthWave(waveform: Waveform) {
        _synthWave.value = waveform
        AudioEngine.activeWaveform = waveform
    }

    // Drum actions
    fun playDrumPad(sound: DrumSound) {
        AudioEngine.triggerDrumPad(sound)
    }

    fun toggleDrumStep(sound: DrumSound, step: Int) {
        AudioEngine.drumGrid[sound.ordinal][step] = !AudioEngine.drumGrid[sound.ordinal][step]
    }

    fun isDrumStepActive(sound: DrumSound, step: Int): Boolean {
        return AudioEngine.drumGrid[sound.ordinal][step]
    }

    // Synth actions
    fun toggleSynthStep(step: Int, pitch: Int) {
        val list = AudioEngine.synthSequence.getOrPut(step) { mutableListOf() }
        val found = list.indexOfFirst { it.first == pitch }
        if (found != -1) {
            list.removeAt(found)
        } else {
            list.add(Pair(pitch, 1.0f))
            // Audition the note
            AudioEngine.triggerSynthKey(pitch, 1.0f)
            viewModelScope.launch {
                kotlinx.coroutines.delay(180)
                AudioEngine.releaseSynthKey(pitch)
            }
        }
    }

    fun isSynthStepActive(step: Int, pitch: Int): Boolean {
        return AudioEngine.synthSequence[step]?.any { it.first == pitch } == true
    }

    // Load sample to Drum Pad
    fun importSampleForPad(context: Context, sound: DrumSound, uri: Uri) {
        viewModelScope.launch {
            _exportStatus.value = "Importing sample..."
            try {
                val decoded = AudioDecoder.decodeToPcm(context, uri)
                if (decoded != null) {
                    AudioEngine.loadCustomSample(sound, decoded.first, decoded.second)
                    _exportStatus.value = "Imported ${sound.displayName} sample successfully!"
                } else {
                    _exportStatus.value = "Failed to decode audio file. Make sure it is a WAV, MP3, or M4A."
                }
            } catch (e: Exception) {
                _exportStatus.value = "Error: ${e.localizedMessage}"
            }
        }
    }

    // Clear pattern
    fun clearGrid() {
        AudioEngine.clearSequencer()
        _currentStep.value = 0
    }

    // Project Persistence
    fun saveProject(name: String) {
        viewModelScope.launch {
            _currentProjectName.value = name
            val id = repository.saveCurrentProject(name, _currentProjectId.value ?: 0)
            _currentProjectId.value = id.toInt()
            _exportStatus.value = "Project saved successfully!"
        }
    }

    fun loadProject(project: ProjectEntity) {
        viewModelScope.launch {
            _currentProjectId.value = project.id
            _currentProjectName.value = project.name
            _bpm.value = project.bpm
            repository.loadProjectIntoEngine(project)
            _exportStatus.value = "Project '${project.name}' loaded."
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProjectById(project.id)
            if (_currentProjectId.value == project.id) {
                _currentProjectId.value = null
                _currentProjectName.value = "Untitled Project"
                clearGrid()
            }
            _exportStatus.value = "Project deleted."
        }
    }

    // Export Wav
    fun exportToDeviceWav(context: Context) {
        _exportStatus.value = "Rendering sequence..."
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "BeatCraft_Sequenced_${System.currentTimeMillis()}.wav")

        AudioEngine.exportToWav(
            outputFile = file,
            onComplete = { savedFile ->
                _exportStatus.value = "WAV exported to Downloads: ${savedFile.name}"
            },
            onError = { err ->
                _exportStatus.value = "Export failed: $err"
            }
        )
    }

    fun clearStatus() {
        _exportStatus.value = null
    }

    // Simulated Sync & Cloud Backup
    fun toggleBluetoothSync() {
        if (_bluetoothSyncStatus.value == "Disconnected") {
            _bluetoothSyncStatus.value = "Searching..."
            viewModelScope.launch {
                kotlinx.coroutines.delay(1500)
                _bluetoothSyncStatus.value = "Synced via Bluetooth MIDI"
                _vstStatus.value = "Bluetooth Clock Sync Latency: 4.2ms"
            }
        } else {
            _bluetoothSyncStatus.value = "Disconnected"
        }
    }

    fun toggleUsbSync() {
        if (_usbSyncStatus.value == "Disconnected") {
            _usbSyncStatus.value = "Connected"
            viewModelScope.launch {
                kotlinx.coroutines.delay(800)
                _usbSyncStatus.value = "Synced (USB MIDI Host)"
                _vstStatus.value = "USB Clock Sync Active (Clock Jitter: 0.1ms)"
            }
        } else {
            _usbSyncStatus.value = "Disconnected"
        }
    }

    fun triggerCloudBackup() {
        if (_cloudSyncing.value) return
        _cloudSyncing.value = true
        _exportStatus.value = "Backing up sequencer project to cloud..."
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _cloudSyncing.value = false
            _exportStatus.value = "Backup complete! All collaboration stems synchronized."
        }
    }

    fun addCollaborator(name: String) {
        val list = _activeCollaborators.value.toMutableList()
        if (!list.contains(name)) {
            list.add(name)
            _activeCollaborators.value = list
            _vstStatus.value = "$name connected to production session."
        }
    }

    fun removeCollaborator(name: String) {
        val list = _activeCollaborators.value.toMutableList()
        if (list.remove(name)) {
            _activeCollaborators.value = list
            _vstStatus.value = "$name left the session."
        }
    }
}

/**
 * Factory for SequencerViewModel
 */
class SequencerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SequencerViewModel::class.java)) {
            val db = ProjectDatabase.getDatabase(context)
            val repo = ProjectRepository(db.projectDao())
            @Suppress("UNCHECKED_CAST")
            return SequencerViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
