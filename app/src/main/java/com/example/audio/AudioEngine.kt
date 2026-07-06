package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.concurrent.thread
import kotlin.math.*

/**
 * Procedural Drum Sounds
 */
enum class DrumSound(val displayName: String) {
    KICK("Kick"),
    SNARE("Snare"),
    HIHAT_CLOSED("Closed Hat"),
    HIHAT_OPEN("Open Hat"),
    CLAP("Clap"),
    TOM("Tom")
}

/**
 * Waveforms for Synth Roll
 */
enum class Waveform {
    SINE, TRIANGLE, SAWTOOTH, SQUARE
}

/**
 * A polyphonic synth voice model
 */
class SynthVoice {
    var pitch: Int = 60 // MIDI Note
    var isActive: Boolean = false
    var sampleIndex: Long = 0
    var velocity: Float = 1.0f

    // ADSR Envelope state
    var envelopeStage: Int = 0 // 0: Idle, 1: Attack, 2: Decay, 3: Sustain, 4: Release
    var envelopeValue: Float = 0f
    var releaseValue: Float = 0f
    var releaseSampleIndex: Long = 0

    fun trigger(midiNote: Int, vel: Float) {
        pitch = midiNote
        velocity = vel
        isActive = true
        sampleIndex = 0
        envelopeStage = 1 // Attack
        envelopeValue = 0f
    }

    fun release() {
        if (isActive && envelopeStage != 4) {
            envelopeStage = 4 // Release
            releaseValue = envelopeValue
            releaseSampleIndex = 0
        }
    }
}

/**
 * Resonant Low-Pass Filter DSP Effect (Moog/Biquad style)
 */
class BiquadFilter {
    var cutoff: Float = 15000f // Hz
    var resonance: Float = 1.0f // Q
    var isEnabled: Boolean = true

    private var x1 = 0f; private var x2 = 0f; private var y1 = 0f; private var y2 = 0f
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f; private var a1 = 0f; private var a2 = 0f

    fun updateCoefficients(sampleRate: Float) {
        if (!isEnabled) return
        val w0 = (2f * PI * cutoff / sampleRate).toFloat()
        val alpha = (sin(w0.toDouble()) / (2f * resonance)).toFloat()
        val cosw0 = cos(w0.toDouble()).toFloat()
        val a0 = 1f + alpha
        b0 = ((1f - cosw0) / 2f) / a0
        b1 = (1f - cosw0) / a0
        b2 = ((1f - cosw0) / 2f) / a0
        a1 = (-2f * cosw0) / a0
        a2 = (1f - alpha) / a0
    }

    fun process(input: Float): Float {
        if (!isEnabled) return input
        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
    }
}

/**
 * Feedback Ping-Pong Stereo Delay DSP Effect
 */
class DelayEffect {
    var delayTimeSeconds: Float = 0.3f
    var feedback: Float = 0.4f
    var wet: Float = 0.0f // 0f (Dry) to 1f (Wet)
    var isEnabled: Boolean = false

    private val sampleRate = 44100
    private val maxDelaySamples = sampleRate * 2 // 2 seconds max
    private val delayBufferL = FloatArray(maxDelaySamples)
    private val delayBufferR = FloatArray(maxDelaySamples)
    private var writeIndex = 0

    fun process(inputL: Float, inputR: Float): Pair<Float, Float> {
        if (!isEnabled || wet == 0f) return Pair(inputL, inputR)
        val delaySamples = (sampleRate * delayTimeSeconds).toInt().coerceIn(1, maxDelaySamples - 1)
        var readIndex = writeIndex - delaySamples
        if (readIndex < 0) readIndex += maxDelaySamples

        val outL = delayBufferL[readIndex]
        val outR = delayBufferR[readIndex]

        // Feedback loop
        delayBufferL[writeIndex] = inputL + outR * feedback
        delayBufferR[writeIndex] = inputR + outL * feedback

        writeIndex = (writeIndex + 1) % maxDelaySamples

        val mixL = inputL * (1f - wet) + outL * wet
        val mixR = inputR * (1f - wet) + outR * wet
        return Pair(mixL, mixR)
    }
}

/**
 * Soft Clipping Tube Distortion DSP Effect
 */
class OverdriveEffect {
    var drive: Float = 2.0f // 1.0 (light) to 8.0 (heavy)
    var mix: Float = 0.0f // 0f (Dry) to 1f (Wet)
    var isEnabled: Boolean = false

    fun process(input: Float): Float {
        if (!isEnabled || mix == 0f) return input
        val gainInput = input * drive
        // Soft clipping waveshaper
        val distorted = if (gainInput > 0f) {
            1f - exp(-gainInput.toDouble()).toFloat()
        } else {
            -1f + exp(gainInput.toDouble()).toFloat()
        }
        return input * (1f - mix) + distorted * mix
    }
}

/**
 * Comb/Allpass Schroeder Reverb DSP Effect
 */
class ReverbEffect {
    var size: Float = 0.6f
    var wet: Float = 0.0f
    var isEnabled: Boolean = false

    private val delay1 = FloatArray(1600)
    private val delay2 = FloatArray(2100)
    private val delay3 = FloatArray(2500)
    private var p1 = 0; private var p2 = 0; private var p3 = 0

    fun process(input: Float): Float {
        if (!isEnabled || wet == 0f) return input
        val out1 = delay1[p1]
        val out2 = delay2[p2]
        val out3 = delay3[p3]

        delay1[p1] = input + out1 * (0.7f * size)
        delay2[p2] = input + out2 * (0.72f * size)
        delay3[p3] = input + out3 * (0.68f * size)

        p1 = (p1 + 1) % delay1.size
        p2 = (p2 + 1) % delay2.size
        p3 = (p3 + 1) % delay3.size

        val verb = (out1 + out2 + out3) / 3f
        return input * (1f - wet) + verb * wet
    }
}

/**
 * Main Audio Engine Singleton class
 */
@SuppressLint("StaticFieldLeak")
object AudioEngine {

    private const val TAG = "AudioEngine"
    private const val SAMPLE_RATE = 44100
    private val rand = Random()

    private var context: Context? = null
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var thread: Thread? = null

    // BPM & Sequencer State
    var bpm = 120
        set(value) {
            field = value.coerceIn(60, 200)
        }
    var currentStep = 0
    private var sampleCountInStep = 0L

    // Tracks & Selections
    var currentTrackBarCount = 1 // 1 bar (16 steps)
    val maxSteps get() = currentTrackBarCount * 16

    // Sequences (6 drum channels, 16 steps each)
    val drumGrid = Array(DrumSound.entries.size) { BooleanArray(64) }
    
    // Melodic sequence: Step index -> list of MIDI notes
    val synthSequence = mutableMapOf<Int, MutableList<Pair<Int, Float>>>() // step -> list of (pitch, velocity)
    
    // Custom audio samples (PCM arrays) for drum pads (can be imported by user)
    val customDrumSamples = Array<FloatArray?>(DrumSound.entries.size) { null }
    val customDrumSampleRates = IntArray(DrumSound.entries.size) { SAMPLE_RATE }

    // Active triggers (for real-time playback or sample indices)
    private class ActiveTrigger(val pcm: FloatArray, var index: Int, val volume: Float)
    private val activeTriggers = mutableListOf<ActiveTrigger>()
    private val triggerMutex = Any()

    // Synth state
    var activeWaveform = Waveform.SAWTOOTH
    val synthVoices = Array(8) { SynthVoice() } // 8 voice polyphonic
    
    // Synth ADSR parameters
    var synthAttack = 0.05f   // seconds
    var synthDecay = 0.15f    // seconds
    var synthSustain = 0.6f   // volume level
    var synthRelease = 0.3f   // seconds

    // Multi-track Recording state
    var isRecordingSeq = false // Recording live triggers into sequencer
    var isVocalRecording = false // Recording microphone input
    var vocalAudioFile: File? = null
    private var audioRecord: AudioRecord? = null
    private var vocalRecordThread: Thread? = null
    private var vocalPlaybackBuffer: FloatArray? = null
    private var vocalPlayIndex = 0
    var isVocalEnabled = false

    // Level metering (dynamically computed peaks)
    var masterLevelL = 0f
    var masterLevelR = 0f

    // VST FX Modules
    val drumOverdrive = OverdriveEffect()
    val drumDelay = DelayEffect()
    val drumReverb = ReverbEffect()

    val synthFilter = BiquadFilter()
    val synthDelay = DelayEffect()
    val synthReverb = ReverbEffect()

    // Faders
    var masterVolume = 0.8f
    var drumVolume = 0.8f
    var synthVolume = 0.7f
    var vocalVolume = 0.8f

    // Flow states for UI
    private val _playbackState = MutableStateFlow(false)
    val playbackState = _playbackState.asStateFlow()

    private val _stepFlow = MutableStateFlow(0)
    val stepFlow = _stepFlow.asStateFlow()

    private val _midiDeviceList = MutableStateFlow<List<String>>(emptyList())
    val midiDeviceList = _midiDeviceList.asStateFlow()

    private val _vstStatus = MutableStateFlow("Vessel FX Modules Active")
    val vstStatus = _vstStatus.asStateFlow()

    // MIDI System
    private var midiManager: MidiManager? = null

    fun initialize(ctx: Context) {
        context = ctx.applicationContext
        setupMidi()
        initializeDefaultDrumSynth()
    }

    /**
     * Set up some simple generated synth presets for drums so we can play immediately!
     */
    private fun initializeDefaultDrumSynth() {
        // We synthesize custom samples for default high-quality drums at startup
        customDrumSamples[DrumSound.KICK.ordinal] = generateSynthKick()
        customDrumSamples[DrumSound.SNARE.ordinal] = generateSynthSnare()
        customDrumSamples[DrumSound.HIHAT_CLOSED.ordinal] = generateSynthHihat(0.04f)
        customDrumSamples[DrumSound.HIHAT_OPEN.ordinal] = generateSynthHihat(0.3f)
        customDrumSamples[DrumSound.CLAP.ordinal] = generateSynthClap()
        customDrumSamples[DrumSound.TOM.ordinal] = generateSynthTom()
    }

    fun startEngine() {
        if (isPlaying) return
        isPlaying = true
        _playbackState.value = true

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        thread = thread(start = true, name = "AudioEngineThread", priority = Thread.MAX_PRIORITY) {
            audioLoop()
        }
    }

    fun stopEngine() {
        isPlaying = false
        _playbackState.value = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        thread = null
    }

    fun clearSequencer() {
        for (grid in drumGrid) {
            grid.fill(false)
        }
        synthSequence.clear()
        vocalPlaybackBuffer = null
    }

    /**
     * Real-time Audio Loop:
     * Generates PCM samples block-by-block and outputs to AudioTrack
     */
    private fun audioLoop() {
        val bufferLength = 512 // 512 frames * 2 (stereo) = 1024 floats (around 11ms latency!)
        val outputBuffer = FloatArray(bufferLength * 2)

        while (isPlaying) {
            val samplesPerStep = ((SAMPLE_RATE * 60f) / (bpm * 4f)).toLong()

            for (i in 0 until bufferLength) {
                // --- SEQUENCER CLOCK ---
                if (sampleCountInStep >= samplesPerStep) {
                    sampleCountInStep = 0L
                    currentStep = (currentStep + 1) % maxSteps
                    _stepFlow.value = currentStep

                    // Trigger step content
                    triggerSequencerStep(currentStep)
                }
                sampleCountInStep++

                // --- GENERATE DRUM SIGNAL ---
                var drumL = 0f
                var drumR = 0f
                synchronized(triggerMutex) {
                    val iterator = activeTriggers.iterator()
                    while (iterator.hasNext()) {
                        val trigger = iterator.next()
                        if (trigger.index < trigger.pcm.size) {
                            val amp = trigger.pcm[trigger.index] * trigger.volume
                            drumL += amp
                            drumR += amp
                            trigger.index++
                        } else {
                            iterator.remove()
                        }
                    }
                }

                // Apply Drum FX (VST Rack)
                drumL = drumOverdrive.process(drumL)
                drumR = drumOverdrive.process(drumR)
                val drumDelayOut = drumDelay.process(drumL, drumR)
                drumL = drumDelayOut.first
                drumR = drumDelayOut.second
                drumL = drumReverb.process(drumL)
                drumR = drumReverb.process(drumR)

                // Apply Drum Volume
                drumL *= drumVolume
                drumR *= drumVolume

                // --- GENERATE SYNTH SIGNAL ---
                var synthMono = 0f
                for (voice in synthVoices) {
                    if (voice.isActive) {
                        val voiceSample = generateSynthSample(voice)
                        synthMono += voiceSample
                    }
                }

                // Apply Synth Resonant Filter (Moog / Biquad)
                synthFilter.updateCoefficients(SAMPLE_RATE.toFloat())
                synthMono = synthFilter.process(synthMono)

                var synthL = synthMono
                var synthR = synthMono

                // Apply Synth Delay & Reverb (VST Rack)
                val synthDelayOut = synthDelay.process(synthL, synthR)
                synthL = synthDelayOut.first
                synthR = synthDelayOut.second
                synthL = synthReverb.process(synthL)
                synthR = synthReverb.process(synthR)

                // Apply Synth Volume
                synthL *= synthVolume
                synthR *= synthVolume

                // --- VOCAL RECORDING SIGNAL ---
                var vocalL = 0f
                var vocalR = 0f
                val vocalBuf = vocalPlaybackBuffer
                if (isVocalEnabled && vocalBuf != null) {
                    if (vocalPlayIndex < vocalBuf.size) {
                        val vSamp = vocalBuf[vocalPlayIndex] * vocalVolume
                        vocalL = vSamp
                        vocalR = vSamp
                        if (isPlaying) {
                            vocalPlayIndex++
                        }
                    } else {
                        // Loop vocal with sequencer
                        if (currentStep == 0 && sampleCountInStep == 0L) {
                            vocalPlayIndex = 0
                        }
                    }
                }

                // --- MIX AND LIMIT ---
                var mixL = drumL + synthL + vocalL
                var mixR = drumR + synthR + vocalR

                // Master volume
                mixL *= masterVolume
                mixR *= masterVolume

                // Master Soft-Limiter (tanh compression to prevent digital clipping)
                mixL = tanh(mixL)
                mixR = tanh(mixR)

                // Level measurement
                val peakL = abs(mixL)
                val peakR = abs(mixR)
                masterLevelL = masterLevelL * 0.99f + peakL * 0.01f
                masterLevelR = masterLevelR * 0.99f + peakR * 0.01f

                outputBuffer[i * 2] = mixL
                outputBuffer[i * 2 + 1] = mixR
            }

            audioTrack?.write(outputBuffer, 0, outputBuffer.size, AudioTrack.WRITE_NON_BLOCKING)
        }
    }

    /**
     * Synthesise real-time audio sample for a polyphonic voice based on ADSR envelope.
     */
    private fun generateSynthSample(voice: SynthVoice): Float {
        val freq = midiNoteToFreq(voice.pitch)
        val t = voice.sampleIndex / SAMPLE_RATE.toFloat()
        voice.sampleIndex++

        // 1. Oscillator wave generation
        var rawWave = 0f
        when (activeWaveform) {
            Waveform.SINE -> rawWave = sin(2f * PI * freq * t).toFloat()
            Waveform.TRIANGLE -> {
                val phase = (freq * t) % 1.0f
                rawWave = 2f * abs(2f * phase - 1f) - 1f
            }
            Waveform.SAWTOOTH -> {
                rawWave = 2f * ((freq * t) % 1.0f) - 1f
            }
            Waveform.SQUARE -> {
                rawWave = if ((freq * t) % 1.0f < 0.5f) 0.4f else -0.4f
            }
        }

        // 2. ADSR Envelope calculation
        val sampleIndex = voice.sampleIndex
        val attackSamples = (synthAttack * SAMPLE_RATE).toLong()
        val decaySamples = (synthDecay * SAMPLE_RATE).toLong()
        val releaseSamples = (synthRelease * SAMPLE_RATE).toLong()

        when (voice.envelopeStage) {
            1 -> { // Attack
                if (sampleIndex < attackSamples && attackSamples > 0) {
                    voice.envelopeValue = sampleIndex / attackSamples.toFloat()
                } else {
                    voice.envelopeValue = 1f
                    voice.envelopeStage = 2 // Transition to Decay
                }
            }
            2 -> { // Decay
                val decayProgressSamples = sampleIndex - attackSamples
                if (decayProgressSamples < decaySamples && decaySamples > 0) {
                    val ratio = decayProgressSamples / decaySamples.toFloat()
                    voice.envelopeValue = 1f - ratio * (1f - synthSustain)
                } else {
                    voice.envelopeValue = synthSustain
                    voice.envelopeStage = 3 // Transition to Sustain
                }
            }
            3 -> { // Sustain
                voice.envelopeValue = synthSustain
            }
            4 -> { // Release
                val relIndex = voice.releaseSampleIndex
                voice.releaseSampleIndex++
                if (relIndex < releaseSamples && releaseSamples > 0) {
                    val ratio = relIndex / releaseSamples.toFloat()
                    voice.envelopeValue = voice.releaseValue * (1f - ratio)
                } else {
                    voice.envelopeValue = 0f
                    voice.isActive = false // Done!
                    voice.envelopeStage = 0
                }
            }
        }

        return rawWave * voice.envelopeValue * voice.velocity
    }

    private fun midiNoteToFreq(note: Int): Float {
        return 440f * 2.0f.pow((note - 69) / 12f)
    }

    /**
     * Play drum pad instantly (real-time trigger)
     */
    fun triggerDrumPad(sound: DrumSound, velocity: Float = 1.0f) {
        val pcm = customDrumSamples[sound.ordinal] ?: return
        synchronized(triggerMutex) {
            activeTriggers.add(ActiveTrigger(pcm, 0, velocity))
        }

        // Live recording support
        if (isRecordingSeq && isPlaying) {
            drumGrid[sound.ordinal][currentStep] = true
        }
    }

    /**
     * Play synth key instantly (real-time trigger)
     */
    fun triggerSynthKey(midiNote: Int, velocity: Float = 1.0f) {
        // Find free voice
        val voice = synthVoices.firstOrNull { !it.isActive } ?: synthVoices.minByOrNull { it.sampleIndex }
        voice?.trigger(midiNote, velocity)

        // Live recording support
        if (isRecordingSeq && isPlaying) {
            val list = synthSequence.getOrPut(currentStep) { mutableListOf() }
            if (list.none { it.first == midiNote }) {
                list.add(Pair(midiNote, velocity))
            }
        }
    }

    fun releaseSynthKey(midiNote: Int) {
        synthVoices.forEach {
            if (it.isActive && it.pitch == midiNote) {
                it.release()
            }
        }
    }

    /**
     * Trigger scheduled sequence notes on step boundaries
     */
    private fun triggerSequencerStep(step: Int) {
        // 1. Play drum step notes
        for (sound in DrumSound.entries) {
            if (drumGrid[sound.ordinal][step]) {
                val pcm = customDrumSamples[sound.ordinal]
                if (pcm != null) {
                    synchronized(triggerMutex) {
                        activeTriggers.add(ActiveTrigger(pcm, 0, 1.0f))
                    }
                }
            }
        }

        // 2. Play synth step notes
        synthSequence[step]?.forEach { (pitch, vel) ->
            val voice = synthVoices.firstOrNull { !it.isActive } ?: synthVoices.minByOrNull { it.sampleIndex }
            voice?.trigger(pitch, vel)

            // Auto-release in step sequencers after a brief time (e.g. 150ms)
            Handler(Looper.getMainLooper()).postDelayed({
                synthVoices.forEach { v ->
                    if (v.isActive && v.pitch == pitch) {
                        v.release()
                    }
                }
            }, 180L)
        }

        // Reset vocal index if starting sequence
        if (step == 0) {
            vocalPlayIndex = 0
        }
    }

    /**
     * Import raw float PCM samples
     */
    fun loadCustomSample(sound: DrumSound, pcm: FloatArray, sampleRate: Int) {
        customDrumSamples[sound.ordinal] = pcm
        customDrumSampleRates[sound.ordinal] = sampleRate
    }

    /**
     * procedural drum synthesizers
     */
    private fun generateSynthKick(): FloatArray {
        val len = (SAMPLE_RATE * 0.25f).toInt() // 250ms decay
        val arr = FloatArray(len)
        for (i in 0 until len) {
            val t = i / SAMPLE_RATE.toFloat()
            val freq = 140f * exp(-28f * t) + 40f
            val amp = exp(-10f * t)
            arr[i] = sin(2f * PI * freq * t).toFloat() * amp
        }
        return arr
    }

    private fun generateSynthSnare(): FloatArray {
        val len = (SAMPLE_RATE * 0.18f).toInt() // 180ms decay
        val arr = FloatArray(len)
        for (i in 0 until len) {
            val t = i / SAMPLE_RATE.toFloat()
            // Sine body
            val tone = sin(2f * PI * 180f * t).toFloat() * exp(-25f * t) * 0.4f
            // Filtered-like noise
            val noiseVal = (rand.nextFloat() * 2f - 1f) * exp(-14f * t) * 0.6f
            arr[i] = tone + noiseVal
        }
        return arr
    }

    private fun generateSynthHihat(decaySec: Float): FloatArray {
        val len = (SAMPLE_RATE * decaySec).toInt()
        val arr = FloatArray(len)
        var lastVal = 0f
        for (i in 0 until len) {
            val t = i / SAMPLE_RATE.toFloat()
            val rawNoise = rand.nextFloat() * 2f - 1f
            // Simple Highpass differential filter
            val filtered = rawNoise - lastVal
            lastVal = rawNoise
            arr[i] = filtered * exp(-t / (decaySec * 0.4f)) * 0.35f
        }
        return arr
    }

    private fun generateSynthClap(): FloatArray {
        val len = (SAMPLE_RATE * 0.22f).toInt()
        val arr = FloatArray(len)
        for (i in 0 until len) {
            val t = i / SAMPLE_RATE.toFloat()
            var amp = 0f
            // 3 mini pre-bursts
            if (t < 0.01f) {
                amp = exp(-150f * t) * 0.4f
            } else if (t < 0.02f) {
                amp = exp(-150f * (t - 0.01f)) * 0.5f
            } else if (t < 0.03f) {
                amp = exp(-150f * (t - 0.02f)) * 0.6f
            } else {
                amp = exp(-16f * (t - 0.03f)) * 0.8f
            }
            val noiseVal = (rand.nextFloat() * 2f - 1f) * amp
            arr[i] = noiseVal * 0.5f
        }
        return arr
    }

    private fun generateSynthTom(): FloatArray {
        val len = (SAMPLE_RATE * 0.3f).toInt()
        val arr = FloatArray(len)
        for (i in 0 until len) {
            val t = i / SAMPLE_RATE.toFloat()
            val freq = 120f * exp(-12f * t) + 55f
            val amp = exp(-6f * t)
            arr[i] = sin(2f * PI * freq * t).toFloat() * amp * 0.6f
        }
        return arr
    }

    /**
     * MIDI System Setup & Controller Integration
     */
    private fun setupMidi() {
        val m = context?.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        if (m == null) {
            Log.w(TAG, "MIDI Service is not supported on this device.")
            return
        }
        midiManager = m

        val devices = m.devices
        val devNames = mutableListOf<String>()
        devices.forEach { info ->
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown MIDI Controller"
            devNames.add(name)
            openMidiDevice(info)
        }
        _midiDeviceList.value = devNames
    }

    private fun openMidiDevice(deviceInfo: MidiDeviceInfo) {
        midiManager?.openDevice(deviceInfo, { device ->
            if (device != null) {
                Log.d(TAG, "Successfully opened MIDI Controller device!")
                val ports = deviceInfo.ports
                ports.forEach { port ->
                    if (port.type == MidiDeviceInfo.PortInfo.TYPE_INPUT) {
                        // MIDI controllers send bytes, so we open as OutputPort from device to receive them!
                        val outputPort = device.openOutputPort(port.portNumber)
                        outputPort?.connect(object : MidiReceiver() {
                            override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                                parseMidiBytes(msg, offset, count)
                            }
                        })
                    }
                }
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun parseMidiBytes(msg: ByteArray, offset: Int, count: Int) {
        var i = offset
        while (i < offset + count) {
            val status = msg[i].toInt() and 0xFF
            if (status >= 0x80 && status < 0xF0) {
                val command = status and 0xF0
                if (i + 2 < offset + count) {
                    val noteNum = msg[i + 1].toInt() and 0x7F
                    val velocity = msg[i + 2].toInt() and 0x7F
                    
                    if (command == 0x90 && velocity > 0) {
                        // MIDI Note ON
                        if (noteNum in 36..43) {
                            // Map low notes (36-43) to Drum Pads
                            val index = (noteNum - 36) % DrumSound.entries.size
                            triggerDrumPad(DrumSound.entries[index], velocity / 127f)
                        } else {
                            // Synthesizer playing
                            triggerSynthKey(noteNum, velocity / 127f)
                        }
                    } else if (command == 0x80 || (command == 0x90 && velocity == 0)) {
                        // MIDI Note OFF
                        releaseSynthKey(noteNum)
                    }
                    i += 3
                } else {
                    break
                }
            } else {
                i++
            }
        }
    }

    /**
     * Start/Stop Real-Time Vocal Microphone Recording (Multi-track recording!)
     */
    @SuppressLint("MissingPermission")
    fun startVocalRecording() {
        if (isVocalRecording) return
        isVocalRecording = true
        isVocalEnabled = false

        vocalAudioFile = File(context?.cacheDir, "vocal_track_${System.currentTimeMillis()}.pcm")
        val recBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            recBufSize
        )

        audioRecord?.startRecording()

        vocalRecordThread = thread(start = true, name = "VocalRecordThread") {
            val fos = FileOutputStream(vocalAudioFile)
            val buffer = ShortArray(recBufSize / 2)
            while (isVocalRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // Convert shorts to little-endian bytes and write to file
                    val byteBuf = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (k in 0 until read) {
                        byteBuf.putShort(buffer[k])
                    }
                    fos.write(byteBuf.array())
                }
            }
            fos.close()
        }
    }

    fun stopVocalRecording() {
        if (!isVocalRecording) return
        isVocalRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        vocalRecordThread = null

        // Load recorded bytes into floating-point playback buffer
        val file = vocalAudioFile ?: return
        if (file.exists()) {
            val bytes = file.readBytes()
            val numShorts = bytes.size / 2
            val floats = FloatArray(numShorts)
            val byteBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until numShorts) {
                val shortVal = byteBuf.short
                floats[i] = shortVal / 32768f
            }
            vocalPlaybackBuffer = floats
            vocalPlayIndex = 0
            isVocalEnabled = true
        }
    }

    /**
     * Offline Sound Pattern Rendering to high-quality stereo WAV files.
     * Generates a fully calculated 16-bit PCM WAV container of the pattern.
     */
    fun exportToWav(outputFile: File, onComplete: (File) -> Unit, onError: (String) -> Unit) {
        thread(start = true) {
            try {
                val bpmCalc = bpm
                val totalSteps = maxSteps
                val samplesPerStep = ((SAMPLE_RATE * 60f) / (bpmCalc * 4f)).toLong()
                val totalFrames = (samplesPerStep * totalSteps).toInt()

                val wavWriter = FileOutputStream(outputFile)
                // Write placeholder WAV header
                writeWavHeader(wavWriter, totalFrames * 2 * 2) // Stereo (2), 16-bit (2 bytes)

                // Instantiate fresh engines for render to avoid interrupting real-time playback
                val synthVoicesOffline = Array(8) { SynthVoice() }
                
                // Track active triggers for drum render
                class RenderTrigger(val pcm: FloatArray, var index: Int)
                val renderTriggers = mutableListOf<RenderTrigger>()

                val outBytes = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)

                // Render block frame-by-frame
                for (frame in 0 until totalFrames) {
                    val step = (frame / samplesPerStep).toInt()
                    val frameInStep = frame % samplesPerStep

                    // Step start trigger
                    if (frameInStep == 0L) {
                        // 1. Drums
                        for (sound in DrumSound.entries) {
                            if (drumGrid[sound.ordinal][step]) {
                                customDrumSamples[sound.ordinal]?.let { pcm ->
                                    renderTriggers.add(RenderTrigger(pcm, 0))
                                }
                            }
                        }
                        // 2. Synth
                        synthSequence[step]?.forEach { (pitch, vel) ->
                            val voice = synthVoicesOffline.firstOrNull { !it.isActive } ?: synthVoicesOffline.minByOrNull { it.sampleIndex }
                            voice?.trigger(pitch, vel)
                        }
                    }

                    // Release synth voices after brief duration during render (approx 180ms)
                    val sampleReleaseLimit = (0.18f * SAMPLE_RATE).toLong()
                    if (frameInStep == sampleReleaseLimit) {
                        synthSequence[step]?.forEach { (pitch, _) ->
                            synthVoicesOffline.forEach { v ->
                                if (v.isActive && v.pitch == pitch) {
                                    v.release()
                                }
                            }
                        }
                    }

                    // Compute Drum Mix
                    var drumL = 0f
                    var drumR = 0f
                    val iterator = renderTriggers.iterator()
                    while (iterator.hasNext()) {
                        val trigger = iterator.next()
                        if (trigger.index < trigger.pcm.size) {
                            val amp = trigger.pcm[trigger.index]
                            drumL += amp
                            drumR += amp
                            trigger.index++
                        } else {
                            iterator.remove()
                        }
                    }

                    // Apply Drum FX (drumOverdrive, drumDelay, drumReverb)
                    drumL = drumOverdrive.process(drumL)
                    drumR = drumOverdrive.process(drumR)
                    val drumDelayOut = drumDelay.process(drumL, drumR)
                    drumL = drumDelayOut.first
                    drumR = drumDelayOut.second
                    drumL = drumReverb.process(drumL)
                    drumR = drumReverb.process(drumR)

                    drumL *= drumVolume
                    drumR *= drumVolume

                    // Compute Synth Mix
                    var synthMono = 0f
                    for (voice in synthVoicesOffline) {
                        if (voice.isActive) {
                            // Standard offline sample generator (simplified sine/saw based on activeWaveform)
                            val f = 440f * 2.0f.pow((voice.pitch - 69) / 12f)
                            val t = voice.sampleIndex / SAMPLE_RATE.toFloat()
                            voice.sampleIndex++

                            var rawWave = 0f
                            when (activeWaveform) {
                                Waveform.SINE -> rawWave = sin(2f * PI * f * t).toFloat()
                                Waveform.TRIANGLE -> rawWave = 2f * abs(2f * ((f * t) % 1.0f) - 1f) - 1f
                                Waveform.SAWTOOTH -> rawWave = 2f * ((f * t) % 1.0f) - 1f
                                Waveform.SQUARE -> rawWave = if ((f * t) % 1.0f < 0.5f) 0.4f else -0.4f
                            }

                            // Minimal ADSR logic for render voice
                            val attackS = (synthAttack * SAMPLE_RATE).toLong()
                            val decayS = (synthDecay * SAMPLE_RATE).toLong()
                            val releaseS = (synthRelease * SAMPLE_RATE).toLong()

                            when (voice.envelopeStage) {
                                1 -> {
                                    if (voice.sampleIndex < attackS && attackS > 0) voice.envelopeValue = voice.sampleIndex / attackS.toFloat()
                                    else { voice.envelopeValue = 1f; voice.envelopeStage = 2 }
                                }
                                2 -> {
                                    val dp = voice.sampleIndex - attackS
                                    if (dp < decayS && decayS > 0) voice.envelopeValue = 1f - (dp / decayS.toFloat()) * (1f - synthSustain)
                                    else { voice.envelopeValue = synthSustain; voice.envelopeStage = 3 }
                                }
                                3 -> voice.envelopeValue = synthSustain
                                4 -> {
                                    val ri = voice.releaseSampleIndex++
                                    if (ri < releaseS && releaseS > 0) voice.envelopeValue = voice.releaseValue * (1f - ri / releaseS.toFloat())
                                    else { voice.envelopeValue = 0f; voice.isActive = false; voice.envelopeStage = 0 }
                                }
                            }
                            synthMono += rawWave * voice.envelopeValue * voice.velocity
                        }
                    }

                    // Apply Synth FX
                    synthMono = synthFilter.process(synthMono)
                    var synthL = synthMono
                    var synthR = synthMono
                    val synthDelayOut = synthDelay.process(synthL, synthR)
                    synthL = synthDelayOut.first
                    synthR = synthDelayOut.second
                    synthL = synthReverb.process(synthL)
                    synthR = synthReverb.process(synthR)

                    synthL *= synthVolume
                    synthR *= synthVolume

                    // Compute Vocal Mix
                    var vocalL = 0f
                    var vocalR = 0f
                    val vocalBuf = vocalPlaybackBuffer
                    if (isVocalEnabled && vocalBuf != null && frame < vocalBuf.size) {
                        val vSamp = vocalBuf[frame] * vocalVolume
                        vocalL = vSamp
                        vocalR = vSamp
                    }

                    // Combine and dynamic limit
                    var mixL = (drumL + synthL + vocalL) * masterVolume
                    var mixR = (drumR + synthR + vocalR) * masterVolume
                    mixL = tanh(mixL).coerceIn(-1.0f, 1.0f)
                    mixR = tanh(mixR).coerceIn(-1.0f, 1.0f)

                    // Convert to 16-bit short
                    val shortL = (mixL * 32767).toInt().toShort()
                    val shortR = (mixR * 32767).toInt().toShort()

                    if (outBytes.remaining() < 4) {
                        wavWriter.write(outBytes.array(), 0, outBytes.position())
                        outBytes.clear()
                    }
                    outBytes.putShort(shortL)
                    outBytes.putShort(shortR)
                }

                if (outBytes.position() > 0) {
                    wavWriter.write(outBytes.array(), 0, outBytes.position())
                }

                wavWriter.close()
                updateWavHeader(outputFile)

                onComplete(outputFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed exporting audio to WAV", e)
                onError(e.localizedMessage ?: "Unknown compilation error")
            }
        }
    }

    private fun writeWavHeader(out: FileOutputStream, totalDataLen: Int) {
        val header = ByteArray(44)
        val totalAudioLen = totalDataLen
        val totalDataPlusHeader = totalDataLen + 36
        val longSampleRate = SAMPLE_RATE.toLong()
        val channels = 2
        val byteRate = (longSampleRate * channels * 16 / 8)

        header[0] = 'R'.code.toByte() // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataPlusHeader and 0xff).toByte()
        header[5] = (totalDataPlusHeader shr 8 and 0xff).toByte()
        header[6] = (totalDataPlusHeader shr 16 and 0xff).toByte()
        header[7] = (totalDataPlusHeader shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte() // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // fmt
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Header length (16 bytes)
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample (16)
        header[35] = 0
        header[36] = 'd'.code.toByte() // data chunk
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        out.write(header, 0, 44)
    }

    private fun updateWavHeader(file: File) {
        val size = file.length().toInt() - 8
        val audioSize = file.length().toInt() - 44
        val raf = RandomAccessFile(file, "rw")
        raf.seek(4) // Seek to totalDataPlusHeader
        raf.writeByte(size and 0xff)
        raf.writeByte(size shr 8 and 0xff)
        raf.writeByte(size shr 16 and 0xff)
        raf.writeByte(size shr 24 and 0xff)
        raf.seek(40) // Seek to totalAudioLen
        raf.writeByte(audioSize and 0xff)
        raf.writeByte(audioSize shr 8 and 0xff)
        raf.writeByte(audioSize shr 16 and 0xff)
        raf.writeByte(audioSize shr 24 and 0xff)
        raf.close()
    }
}
