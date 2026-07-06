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
import com.example.audio.DrumVoiceParams
import com.example.audio.MusicTheory
import com.example.audio.ScaleType
import com.example.audio.SoundPack
import com.example.audio.SoundPackLibrary
import com.example.audio.SynthPatch
import com.example.audio.Waveform
import com.example.database.PresetEntity
import com.example.database.PresetRepository
import com.example.database.ProjectDatabase
import com.example.database.ProjectEntity
import com.example.database.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SequencerViewModel(
    private val repository: ProjectRepository,
    private val presetRepository: PresetRepository
) : ViewModel() {

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

    // Melody assistance: key/scale used to highlight and optionally restrict piano roll entry
    private val _scaleRoot = MutableStateFlow(0) // 0 = C .. 11 = B
    val scaleRoot = _scaleRoot.asStateFlow()

    private val _scaleType = MutableStateFlow(ScaleType.MAJOR)
    val scaleType = _scaleType.asStateFlow()

    private val _scaleLockEnabled = MutableStateFlow(false)
    val scaleLockEnabled = _scaleLockEnabled.asStateFlow()

    // Sound packs & community library
    val activeSoundPackName: StateFlow<String> = AudioEngine.activeSoundPackName
    val builtInSoundPacks: List<SoundPack> = SoundPackLibrary.builtInPacks

    val userSoundPackPresets: StateFlow<List<PresetEntity>> = presetRepository.allPresets
        .map { list -> list.filter { it.type == PresetRepository.TYPE_SOUND_PACK } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userSynthPresets: StateFlow<List<PresetEntity>> = presetRepository.allPresets
        .map { list -> list.filter { it.type == PresetRepository.TYPE_SYNTH_PATCH } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            // Scale Lock keeps hobbyists from placing clashing out-of-key notes by accident
            if (_scaleLockEnabled.value && !isNoteInScale(pitch)) return
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

    // Melody assistance: key/scale
    fun setScaleRoot(root: Int) { _scaleRoot.value = root }
    fun setScaleType(type: ScaleType) { _scaleType.value = type }
    fun toggleScaleLock() { _scaleLockEnabled.value = !_scaleLockEnabled.value }
    fun isNoteInScale(note: Int): Boolean = MusicTheory.isInScale(_scaleRoot.value, _scaleType.value, note)

    // Per-track mute (silences a drum voice in playback/export; pads still audition live)
    fun toggleDrumMute(sound: DrumSound) {
        AudioEngine.drumMuted[sound.ordinal] = !AudioEngine.drumMuted[sound.ordinal]
    }
    fun isDrumMuted(sound: DrumSound): Boolean = AudioEngine.drumMuted[sound.ordinal]

    // Rapid sequencing workflow tools
    fun duplicateLastBar() {
        val barCount = getBarCount()
        if (barCount <= 1) return
        val bar = 16
        val lastBarStart = (barCount - 1) * bar
        val prevBarStart = (barCount - 2) * bar
        for (sound in DrumSound.entries) {
            val grid = AudioEngine.drumGrid[sound.ordinal]
            for (i in 0 until bar) {
                grid[lastBarStart + i] = grid[prevBarStart + i]
            }
        }
        for (i in 0 until bar) {
            val src = AudioEngine.synthSequence[prevBarStart + i]
            if (src != null) {
                AudioEngine.synthSequence[lastBarStart + i] = src.toMutableList()
            } else {
                AudioEngine.synthSequence.remove(lastBarStart + i)
            }
        }
    }

    fun randomizeDrumTrack(sound: DrumSound, density: Float = 0.35f) {
        val totalSteps = getBarCount() * 16
        val grid = AudioEngine.drumGrid[sound.ordinal]
        for (i in 0 until totalSteps) {
            grid[i] = kotlin.random.Random.nextFloat() < density
        }
    }

    fun clearDrumTrack(sound: DrumSound) {
        val totalSteps = getBarCount() * 16
        val grid = AudioEngine.drumGrid[sound.ordinal]
        for (i in 0 until totalSteps) grid[i] = false
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

    // Sound design (per-pad procedural synthesis tweaks)
    fun getDrumVoiceParams(sound: DrumSound): DrumVoiceParams = AudioEngine.drumVoiceParams[sound.ordinal]

    fun updateDrumVoiceParams(sound: DrumSound, params: DrumVoiceParams) {
        AudioEngine.updateDrumVoiceParams(sound, params)
    }

    // Sound packs
    fun applySoundPack(pack: SoundPack) {
        AudioEngine.applySoundPack(pack)
        _exportStatus.value = "Applied sound pack: ${pack.name}"
    }

    fun applyUserSoundPackPreset(entity: PresetEntity) {
        presetRepository.decodeSoundPack(entity)?.let { applySoundPack(it) }
    }

    fun saveCurrentAsSoundPack(name: String, author: String) {
        viewModelScope.launch {
            val params = DrumSound.entries.associate { it.name to AudioEngine.drumVoiceParams[it.ordinal] }
            val pack = SoundPack(
                id = "user_${System.currentTimeMillis()}",
                name = name,
                author = author.ifBlank { "Anonymous" },
                description = "Custom pad tuning shared from BeatCraft Workstation.",
                voiceParams = params
            )
            presetRepository.saveSoundPack(pack, pack.author)
            _exportStatus.value = "Saved sound pack '$name' to your library!"
        }
    }

    fun importSoundPackFromJson(json: String) {
        viewModelScope.launch {
            val pack = presetRepository.parseSoundPackJson(json)
            if (pack != null) {
                presetRepository.saveSoundPack(pack, pack.author)
                _exportStatus.value = "Imported sound pack '${pack.name}'"
            } else {
                _exportStatus.value = "Could not parse that sound pack JSON."
            }
        }
    }

    // Synth presets
    fun saveCurrentAsSynthPreset(name: String, author: String) {
        viewModelScope.launch {
            val patch = SynthPatch(
                waveform = AudioEngine.activeWaveform.name,
                attack = AudioEngine.synthAttack,
                decay = AudioEngine.synthDecay,
                sustain = AudioEngine.synthSustain,
                release = AudioEngine.synthRelease,
                filterCutoff = AudioEngine.synthFilter.cutoff,
                filterResonance = AudioEngine.synthFilter.resonance
            )
            presetRepository.saveSynthPatch(name, author.ifBlank { "Anonymous" }, patch)
            _exportStatus.value = "Saved synth preset '$name' to your library!"
        }
    }

    fun applySynthPreset(entity: PresetEntity) {
        val patch = presetRepository.decodeSynthPatch(entity) ?: return
        AudioEngine.activeWaveform = runCatching { Waveform.valueOf(patch.waveform) }.getOrDefault(AudioEngine.activeWaveform)
        _synthWave.value = AudioEngine.activeWaveform
        AudioEngine.synthAttack = patch.attack
        AudioEngine.synthDecay = patch.decay
        AudioEngine.synthSustain = patch.sustain
        AudioEngine.synthRelease = patch.release
        AudioEngine.synthFilter.cutoff = patch.filterCutoff
        AudioEngine.synthFilter.resonance = patch.filterResonance
        _exportStatus.value = "Applied synth preset '${entity.name}'"
    }

    fun importSynthPresetFromJson(json: String) {
        viewModelScope.launch {
            val patch = presetRepository.parseSynthPatchJson(json)
            if (patch != null) {
                presetRepository.saveSynthPatch("Imported Preset", "Community", patch)
                _exportStatus.value = "Imported synth preset into your library."
            } else {
                _exportStatus.value = "Could not parse that synth preset JSON."
            }
        }
    }

    fun deletePreset(entity: PresetEntity) {
        viewModelScope.launch {
            presetRepository.deletePreset(entity.id)
            _exportStatus.value = "Removed '${entity.name}' from your library."
        }
    }

    // Exports live under app-specific storage (no runtime storage permission needed on any
    // Android version) and are shared out via FileProvider from the Library tab.
    private fun exportsDir(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Exports").apply { mkdirs() }

    fun listExportedFiles(context: Context): List<File> =
        exportsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun exportStems(context: Context) {
        _exportStatus.value = "Rendering stems..."
        val baseName = "BeatCraft_${System.currentTimeMillis()}"
        AudioEngine.exportStemsToWav(
            outputDir = exportsDir(context),
            baseName = baseName,
            onComplete = { files -> _exportStatus.value = "Exported ${files.size} stems. Share them from the Library tab!" },
            onError = { err -> _exportStatus.value = "Stem export failed: $err" }
        )
    }

    fun exportMidiFile(context: Context) {
        viewModelScope.launch {
            try {
                val file = File(exportsDir(context), "BeatCraft_${System.currentTimeMillis()}.mid")
                AudioEngine.exportMidiFile(file)
                _exportStatus.value = "MIDI file exported: ${file.name}"
            } catch (e: Exception) {
                _exportStatus.value = "MIDI export failed: ${e.localizedMessage}"
            }
        }
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
        val file = File(exportsDir(context), "BeatCraft_Sequenced_${System.currentTimeMillis()}.wav")

        AudioEngine.exportToWav(
            outputFile = file,
            onComplete = { savedFile ->
                _exportStatus.value = "WAV exported: ${savedFile.name} (share it from the Library tab)"
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
            val presetRepo = PresetRepository(db.presetDao())
            @Suppress("UNCHECKED_CAST")
            return SequencerViewModel(repo, presetRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
