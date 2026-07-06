package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sequencer_projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val bpm: Int = 120,
    val barCount: Int = 1,
    val drumPatternJson: String, // 2D array representation of active steps
    val synthPatternJson: String, // Step -> MIDI notes map
    val vocalFilePath: String? = null,
    val lastModified: Long = System.currentTimeMillis()
)
