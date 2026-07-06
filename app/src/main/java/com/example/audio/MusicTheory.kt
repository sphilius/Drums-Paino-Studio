package com.example.audio

/**
 * Scale definitions used by the piano roll to highlight in-key notes and
 * optionally restrict note entry, so hobbyist producers can build melodies
 * without needing formal music theory training.
 */
enum class ScaleType(val displayName: String, val intervals: List<Int>) {
    MAJOR("Major", listOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR("Minor", listOf(0, 2, 3, 5, 7, 8, 10)),
    DORIAN("Dorian", listOf(0, 2, 3, 5, 7, 9, 10)),
    MIXOLYDIAN("Mixolydian", listOf(0, 2, 4, 5, 7, 9, 10)),
    MAJOR_PENTATONIC("Major Pent.", listOf(0, 2, 4, 7, 9)),
    MINOR_PENTATONIC("Minor Pent.", listOf(0, 3, 5, 7, 10)),
    BLUES("Blues", listOf(0, 3, 5, 6, 7, 10)),
    CHROMATIC("Chromatic", (0..11).toList())
}

object MusicTheory {
    val pitchClassNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun noteName(midiNote: Int): String {
        val octave = (midiNote / 12) - 1
        return "${pitchClassNames[((midiNote % 12) + 12) % 12]}$octave"
    }

    fun isBlackKey(midiNote: Int): Boolean = pitchClassNames[((midiNote % 12) + 12) % 12].contains("#")

    /**
     * @param rootPitchClass 0 (C) .. 11 (B)
     */
    fun isInScale(rootPitchClass: Int, scale: ScaleType, midiNote: Int): Boolean {
        val degree = ((midiNote - rootPitchClass) % 12 + 12) % 12
        return scale.intervals.contains(degree)
    }
}
