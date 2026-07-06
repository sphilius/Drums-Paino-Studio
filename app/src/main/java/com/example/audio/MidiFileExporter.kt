package com.example.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Minimal Standard MIDI File (format 0) writer so patterns built in the
 * drum grid and piano roll can be opened directly in any external DAW,
 * rounding out this app's MIDI support beyond controller input.
 */
object MidiFileExporter {
    private const val TICKS_PER_QUARTER = 96
    private const val DRUM_CHANNEL = 9 // MIDI channel 10 (General MIDI percussion)
    private const val SYNTH_CHANNEL = 0

    private val drumMidiNotes = mapOf(
        DrumSound.KICK to 36,
        DrumSound.SNARE to 38,
        DrumSound.HIHAT_CLOSED to 42,
        DrumSound.HIHAT_OPEN to 46,
        DrumSound.CLAP to 39,
        DrumSound.TOM to 45
    )

    private data class MidiEvent(val tick: Long, val isNoteOn: Boolean, val channel: Int, val note: Int, val velocity: Int)

    fun export(
        outputFile: File,
        bpm: Int,
        totalSteps: Int,
        drumGrid: Array<BooleanArray>,
        drumMuted: BooleanArray,
        synthSequence: Map<Int, List<Pair<Int, Float>>>
    ): File {
        val ticksPerStep = TICKS_PER_QUARTER / 4L // sequencer steps are 16th notes
        val events = mutableListOf<MidiEvent>()

        for (step in 0 until totalSteps) {
            val tick = step * ticksPerStep

            DrumSound.entries.forEach { sound ->
                if (!drumMuted[sound.ordinal] && drumGrid[sound.ordinal].getOrElse(step) { false }) {
                    val note = drumMidiNotes[sound] ?: return@forEach
                    events += MidiEvent(tick, true, DRUM_CHANNEL, note, 100)
                    events += MidiEvent(tick + ticksPerStep / 2, false, DRUM_CHANNEL, note, 0)
                }
            }

            synthSequence[step]?.forEach { (pitch, velocity) ->
                val vel = (velocity * 127f).roundToInt().coerceIn(1, 127)
                events += MidiEvent(tick, true, SYNTH_CHANNEL, pitch, vel)
                events += MidiEvent(tick + (ticksPerStep - 2).coerceAtLeast(1), false, SYNTH_CHANNEL, pitch, 0)
            }
        }

        events.sortWith(compareBy({ it.tick }, { it.isNoteOn }))

        FileOutputStream(outputFile).use { out ->
            out.write(headerChunk())
            out.write(trackChunk(buildTrackBytes(bpm, events)))
        }
        return outputFile
    }

    private fun headerChunk(): ByteArray = byteArrayOf(
        'M'.code.toByte(), 'T'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte(),
        0, 0, 0, 6,
        0, 0, // format 0
        0, 1, // 1 track
        (TICKS_PER_QUARTER shr 8).toByte(), (TICKS_PER_QUARTER and 0xFF).toByte()
    )

    private fun trackChunk(data: ByteArray): ByteArray {
        val header = byteArrayOf(
            'M'.code.toByte(), 'T'.code.toByte(), 'r'.code.toByte(), 'k'.code.toByte(),
            (data.size shr 24 and 0xFF).toByte(),
            (data.size shr 16 and 0xFF).toByte(),
            (data.size shr 8 and 0xFF).toByte(),
            (data.size and 0xFF).toByte()
        )
        return header + data
    }

    private fun buildTrackBytes(bpm: Int, events: List<MidiEvent>): ByteArray {
        val out = ByteArrayOutputStream()

        // Tempo meta event at tick 0
        val microsPerQuarter = 60_000_000L / bpm.coerceIn(1, 300)
        out.write(0) // delta time
        out.write(byteArrayOf(0xFF.toByte(), 0x51, 0x03))
        out.write((microsPerQuarter shr 16 and 0xFF).toInt())
        out.write((microsPerQuarter shr 8 and 0xFF).toInt())
        out.write((microsPerQuarter and 0xFF).toInt())

        var lastTick = 0L
        for (event in events) {
            val delta = (event.tick - lastTick).coerceAtLeast(0)
            lastTick = event.tick
            out.write(encodeVarLen(delta))

            val statusByte = (if (event.isNoteOn) 0x90 else 0x80) or event.channel
            out.write(statusByte)
            out.write(event.note and 0x7F)
            out.write(event.velocity and 0x7F)
        }

        out.write(0) // delta time
        out.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00)) // end of track

        return out.toByteArray()
    }

    /** Standard MIDI variable-length quantity encoding (7 bits per byte, MSB continuation flag). */
    private fun encodeVarLen(value: Long): ByteArray {
        var v = value
        val groups = mutableListOf((v and 0x7F).toInt())
        v = v shr 7
        while (v > 0) {
            groups.add(((v and 0x7F) or 0x80).toInt())
            v = v shr 7
        }
        return groups.reversed().map { it.toByte() }.toByteArray()
    }
}
