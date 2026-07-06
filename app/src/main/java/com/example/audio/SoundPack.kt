package com.example.audio

import com.squareup.moshi.JsonClass

/**
 * Procedural synthesis recipe for a single drum voice. Tune/decay/tone are
 * generic knobs whose meaning is adapted per-voice inside AudioEngine's
 * generator functions (e.g. "tone" blends noise vs. tonal body for a snare,
 * but blends transient click vs. sub body for a kick).
 */
@JsonClass(generateAdapter = true)
data class DrumVoiceParams(
    val tune: Float = 0f,      // semitone offset, roughly -12..12
    val decay: Float = 1f,     // envelope length multiplier, roughly 0.3..2.5
    val tone: Float = 0.5f     // voice-specific timbre blend, 0..1
)

/**
 * A shareable, fully procedural drum kit: no audio assets are bundled, so a
 * pack is just a small JSON recipe that can be pasted/emailed/DM'd to
 * another producer and re-synthesized bit-for-bit on their device.
 */
@JsonClass(generateAdapter = true)
data class SoundPack(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val voiceParams: Map<String, DrumVoiceParams>
)

/** A shareable synth patch (oscillator + envelope + filter settings). */
@JsonClass(generateAdapter = true)
data class SynthPatch(
    val waveform: String,
    val attack: Float,
    val decay: Float,
    val sustain: Float,
    val release: Float,
    val filterCutoff: Float,
    val filterResonance: Float
)

object SoundPackLibrary {
    val builtInPacks: List<SoundPack> = listOf(
        SoundPack(
            id = "studio_standard",
            name = "Studio Standard",
            author = "BeatCraft",
            description = "Clean, balanced procedural kit for general purpose beatmaking.",
            voiceParams = DrumSound.entries.associate { it.name to DrumVoiceParams() }
        ),
        SoundPack(
            id = "808_trap_heat",
            name = "808 Trap Heat",
            author = "BeatCraft",
            description = "Deep boomy kick, snappy snare and tight bright hats for trap beats.",
            voiceParams = mapOf(
                DrumSound.KICK.name to DrumVoiceParams(tune = -4f, decay = 1.8f, tone = 0.7f),
                DrumSound.SNARE.name to DrumVoiceParams(tune = 2f, decay = 0.9f, tone = 0.3f),
                DrumSound.HIHAT_CLOSED.name to DrumVoiceParams(decay = 0.6f, tone = 0.8f),
                DrumSound.HIHAT_OPEN.name to DrumVoiceParams(decay = 1.3f, tone = 0.7f),
                DrumSound.CLAP.name to DrumVoiceParams(decay = 1.1f, tone = 0.6f),
                DrumSound.TOM.name to DrumVoiceParams(tune = -3f, decay = 1.4f, tone = 0.4f)
            )
        ),
        SoundPack(
            id = "lofi_dust",
            name = "Lo-Fi Dust",
            author = "BeatCraft",
            description = "Muffled, dusty textures for chill and boom-bap production.",
            voiceParams = mapOf(
                DrumSound.KICK.name to DrumVoiceParams(tune = -2f, decay = 0.8f, tone = 0.2f),
                DrumSound.SNARE.name to DrumVoiceParams(tune = -1f, decay = 0.7f, tone = 0.65f),
                DrumSound.HIHAT_CLOSED.name to DrumVoiceParams(decay = 0.5f, tone = 0.25f),
                DrumSound.HIHAT_OPEN.name to DrumVoiceParams(decay = 0.8f, tone = 0.25f),
                DrumSound.CLAP.name to DrumVoiceParams(decay = 0.8f, tone = 0.3f),
                DrumSound.TOM.name to DrumVoiceParams(tune = -2f, decay = 0.9f, tone = 0.6f)
            )
        ),
        SoundPack(
            id = "acoustic_room",
            name = "Acoustic Room",
            author = "BeatCraft",
            description = "Roomier, natural-feeling kit with longer tails and airy tone.",
            voiceParams = mapOf(
                DrumSound.KICK.name to DrumVoiceParams(tune = 1f, decay = 1.2f, tone = 0.55f),
                DrumSound.SNARE.name to DrumVoiceParams(decay = 1.3f, tone = 0.55f),
                DrumSound.HIHAT_CLOSED.name to DrumVoiceParams(decay = 1.0f, tone = 0.55f),
                DrumSound.HIHAT_OPEN.name to DrumVoiceParams(decay = 1.5f, tone = 0.6f),
                DrumSound.CLAP.name to DrumVoiceParams(decay = 1.4f, tone = 0.55f),
                DrumSound.TOM.name to DrumVoiceParams(tune = 2f, decay = 1.6f, tone = 0.5f)
            )
        )
    )
}
