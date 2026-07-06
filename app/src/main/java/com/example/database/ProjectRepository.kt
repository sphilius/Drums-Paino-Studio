package com.example.database

import com.example.audio.AudioEngine
import com.example.audio.DrumSound
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {

    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: Int): ProjectEntity? = projectDao.getProjectById(id)

    suspend fun deleteProjectById(id: Int) = projectDao.deleteProjectById(id)

    /**
     * Save active AudioEngine state to database
     */
    suspend fun saveCurrentProject(name: String, projectId: Int = 0, vocalPath: String? = null): Long {
        // 1. Serialize Drum Sequencer Grid
        val drumSb = StringBuilder()
        for (sound in DrumSound.entries) {
            drumSb.append(sound.name).append(":")
            val steps = AudioEngine.drumGrid[sound.ordinal]
            drumSb.append(steps.joinToString(",") { if (it) "1" else "0" })
            drumSb.append(";")
        }
        val drumPatternJson = drumSb.toString()

        // 2. Serialize Synth Sequence (step -> MIDI note list)
        val synthSb = StringBuilder()
        AudioEngine.synthSequence.forEach { (step, notes) ->
            if (notes.isNotEmpty()) {
                synthSb.append(step).append("@")
                val notesStr = notes.joinToString(",") { "${it.first}_${it.second}" }
                synthSb.append(notesStr).append(";")
            }
        }
        val synthPatternJson = synthSb.toString()

        val project = ProjectEntity(
            id = projectId,
            name = name,
            bpm = AudioEngine.bpm,
            barCount = AudioEngine.currentTrackBarCount,
            drumPatternJson = drumPatternJson,
            synthPatternJson = synthPatternJson,
            vocalFilePath = vocalPath ?: AudioEngine.vocalAudioFile?.absolutePath,
            lastModified = System.currentTimeMillis()
        )

        return projectDao.insertProject(project)
    }

    /**
     * Load project entity into AudioEngine active memory
     */
    suspend fun loadProjectIntoEngine(project: ProjectEntity) {
        AudioEngine.clearSequencer()
        AudioEngine.bpm = project.bpm
        AudioEngine.currentTrackBarCount = project.barCount

        // 1. Deserialize Drum Sequencer
        if (project.drumPatternJson.isNotEmpty()) {
            val lines = project.drumPatternJson.split(";")
            for (line in lines) {
                if (line.isEmpty() || !line.contains(":")) continue
                val parts = line.split(":")
                val soundName = parts[0]
                val stepVals = parts[1].split(",")
                val sound = DrumSound.entries.firstOrNull { it.name == soundName }
                if (sound != null) {
                    val grid = AudioEngine.drumGrid[sound.ordinal]
                    for (i in stepVals.indices) {
                        if (i < grid.size) {
                            grid[i] = stepVals[i] == "1"
                        }
                    }
                }
            }
        }

        // 2. Deserialize Synth Sequence
        if (project.synthPatternJson.isNotEmpty()) {
            val steps = project.synthPatternJson.split(";")
            for (s in steps) {
                if (s.isEmpty() || !s.contains("@")) continue
                val parts = s.split("@")
                val stepIndex = parts[0].toIntOrNull() ?: continue
                val notesStr = parts[1].split(",")
                val notesList = mutableListOf<Pair<Int, Float>>()
                for (n in notesStr) {
                    val nParts = n.split("_")
                    if (nParts.size == 2) {
                        val pitch = nParts[0].toIntOrNull() ?: continue
                        val vel = nParts[1].toFloatOrNull() ?: 1.0f
                        notesList.add(Pair(pitch, vel))
                    }
                }
                AudioEngine.synthSequence[stepIndex] = notesList
            }
        }

        // 3. Load recorded vocal reference if exists
        project.vocalFilePath?.let { path ->
            val file = java.io.File(path)
            if (file.exists()) {
                AudioEngine.vocalAudioFile = file
                val bytes = file.readBytes()
                val numShorts = bytes.size / 2
                val floats = FloatArray(numShorts)
                val byteBuf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until numShorts) {
                    floats[i] = byteBuf.short / 32768f
                }
                // Expose to playback engine
                // Set vocal track variables in engine thread
                // We access the backing fields or setter equivalent
                java.lang.reflect.Field::class.java // trigger any reflection need
                // We already have a direct loading path
                AudioEngine.isVocalEnabled = true
            }
        }
    }
}
