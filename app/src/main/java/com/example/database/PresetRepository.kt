package com.example.database

import com.example.audio.SoundPack
import com.example.audio.SynthPatch
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow

/**
 * Local-first "community library": presets are plain JSON, so a user can save a
 * Sound Pack or Synth Patch, share the JSON text through any messaging/email app,
 * and another producer can paste it back in to recreate the exact same sound.
 */
class PresetRepository(private val presetDao: PresetDao) {
    private val moshi = Moshi.Builder().build()
    private val soundPackAdapter = moshi.adapter(SoundPack::class.java)
    private val synthPatchAdapter = moshi.adapter(SynthPatch::class.java)

    val allPresets: Flow<List<PresetEntity>> = presetDao.getAllPresets()

    suspend fun saveSoundPack(pack: SoundPack, author: String): Long {
        val entity = PresetEntity(
            name = pack.name,
            author = author,
            type = TYPE_SOUND_PACK,
            payloadJson = soundPackAdapter.toJson(pack)
        )
        return presetDao.insertPreset(entity)
    }

    suspend fun saveSynthPatch(name: String, author: String, patch: SynthPatch): Long {
        val entity = PresetEntity(
            name = name,
            author = author,
            type = TYPE_SYNTH_PATCH,
            payloadJson = synthPatchAdapter.toJson(patch)
        )
        return presetDao.insertPreset(entity)
    }

    fun decodeSoundPack(entity: PresetEntity): SoundPack? =
        if (entity.type == TYPE_SOUND_PACK) runCatching { soundPackAdapter.fromJson(entity.payloadJson) }.getOrNull() else null

    fun decodeSynthPatch(entity: PresetEntity): SynthPatch? =
        if (entity.type == TYPE_SYNTH_PATCH) runCatching { synthPatchAdapter.fromJson(entity.payloadJson) }.getOrNull() else null

    fun parseSoundPackJson(json: String): SoundPack? = runCatching { soundPackAdapter.fromJson(json) }.getOrNull()

    fun parseSynthPatchJson(json: String): SynthPatch? = runCatching { synthPatchAdapter.fromJson(json) }.getOrNull()

    suspend fun deletePreset(id: Int) = presetDao.deletePresetById(id)

    companion object {
        const val TYPE_SOUND_PACK = "SOUND_PACK"
        const val TYPE_SYNTH_PATCH = "SYNTH_PATCH"
    }
}
