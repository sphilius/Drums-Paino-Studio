package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Community-library entry: either a Sound Pack or a Synth Patch, stored as a JSON payload. */
@Entity(tableName = "community_presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val author: String,
    val type: String, // "SOUND_PACK" or "SYNTH_PATCH"
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
