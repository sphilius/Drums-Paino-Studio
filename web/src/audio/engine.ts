import { DRUM_SOUNDS, defaultVoiceParams, type DrumSound, type DrumVoiceParams, type SoundPack, type SynthPatch } from './soundPacks'
import { generateDrumSamples, toAudioBuffer } from './drumSynth'
import { audioBufferToWav } from './wavEncoder'
import { exportMidiFile, type SynthNote } from './midiExporter'

export type Waveform = 'SINE' | 'TRIANGLE' | 'SAWTOOTH' | 'SQUARE'
export const WAVEFORMS: Waveform[] = ['SINE', 'TRIANGLE', 'SAWTOOTH', 'SQUARE']

export interface AdsrParams {
  attack: number
  decay: number
  sustain: number
  release: number
}

const SAMPLE_RATE = 44100
const SYNTH_HOLD_SECONDS = 0.18 // matches the Kotlin app's ~180ms auto-release for sequenced/auditioned notes

function midiToFreq(note: number): number {
  return 440 * Math.pow(2, (note - 69) / 12)
}

function waveformToOscType(waveform: Waveform): OscillatorType {
  switch (waveform) {
    case 'SINE':
      return 'sine'
    case 'TRIANGLE':
      return 'triangle'
    case 'SAWTOOTH':
      return 'sawtooth'
    case 'SQUARE':
      return 'square'
  }
}

interface Graph {
  ctx: BaseAudioContext
  master: GainNode
  drumBus: GainNode
  synthBus: GainNode
  filter: BiquadFilterNode
}

function buildGraph(
  ctx: BaseAudioContext,
  destination: AudioNode,
  opts: { masterVolume: number; drumVolume: number; synthVolume: number; filterCutoff: number; filterResonance: number }
): Graph {
  const master = ctx.createGain()
  master.gain.value = opts.masterVolume
  master.connect(destination)

  const drumBus = ctx.createGain()
  drumBus.gain.value = opts.drumVolume
  drumBus.connect(master)

  const filter = ctx.createBiquadFilter()
  filter.type = 'lowpass'
  filter.frequency.value = opts.filterCutoff
  filter.Q.value = opts.filterResonance
  filter.connect(master)

  const synthBus = ctx.createGain()
  synthBus.gain.value = opts.synthVolume
  synthBus.connect(filter)

  return { ctx, master, drumBus, synthBus, filter }
}

function scheduleDrumHit(ctx: BaseAudioContext, bus: GainNode, buffer: AudioBuffer, time: number, velocity: number) {
  const src = ctx.createBufferSource()
  src.buffer = buffer
  const gain = ctx.createGain()
  gain.gain.value = velocity
  src.connect(gain)
  gain.connect(bus)
  src.start(Math.max(time, ctx.currentTime))
}

function scheduleSynthNote(
  ctx: BaseAudioContext,
  bus: GainNode,
  waveform: Waveform,
  adsr: AdsrParams,
  time: number,
  pitch: number,
  velocity: number,
  holdSeconds: number
) {
  const osc = ctx.createOscillator()
  osc.type = waveformToOscType(waveform)
  osc.frequency.value = midiToFreq(pitch)
  const gain = ctx.createGain()
  osc.connect(gain)
  gain.connect(bus)

  const a = Math.max(0.001, adsr.attack)
  const d = Math.max(0.001, adsr.decay)
  const s = Math.max(0, Math.min(1, adsr.sustain))
  const r = Math.max(0.001, adsr.release)
  const startAt = Math.max(time, ctx.currentTime)

  gain.gain.setValueAtTime(0, startAt)
  gain.gain.linearRampToValueAtTime(velocity, startAt + a)
  gain.gain.linearRampToValueAtTime(velocity * s, startAt + a + d)
  const releaseStart = startAt + a + d + holdSeconds
  gain.gain.setValueAtTime(velocity * s, releaseStart)
  gain.gain.linearRampToValueAtTime(0, releaseStart + r)

  osc.start(startAt)
  osc.stop(releaseStart + r + 0.02)
}

function makeEmptyDrumGrid(): Record<DrumSound, boolean[]> {
  const grid = {} as Record<DrumSound, boolean[]>
  for (const sound of DRUM_SOUNDS) grid[sound] = new Array(64).fill(false)
  return grid
}

function makeDefaultVoiceParams(): Record<DrumSound, DrumVoiceParams> {
  const p = {} as Record<DrumSound, DrumVoiceParams>
  for (const sound of DRUM_SOUNDS) p[sound] = defaultVoiceParams()
  return p
}

function makeDefaultMuted(): Record<DrumSound, boolean> {
  const m = {} as Record<DrumSound, boolean>
  for (const sound of DRUM_SOUNDS) m[sound] = false
  return m
}

export interface StemBlob {
  label: string
  blob: Blob
}

/**
 * Central sequencer + Web Audio engine. Mirrors the Kotlin AudioEngine singleton:
 * one shared instance holds pattern data, live playback state, and export logic.
 */
class Engine {
  // ---- Pattern data ----
  bpm = 120
  barCount = 1
  drumGrid: Record<DrumSound, boolean[]> = makeEmptyDrumGrid()
  synthSequence = new Map<number, SynthNote[]>()
  drumVoiceParams: Record<DrumSound, DrumVoiceParams> = makeDefaultVoiceParams()
  drumMuted: Record<DrumSound, boolean> = makeDefaultMuted()
  activeSoundPackName = 'Studio Standard'

  activeWaveform: Waveform = 'SAWTOOTH'
  adsr: AdsrParams = { attack: 0.05, decay: 0.15, sustain: 0.6, release: 0.3 }
  filterCutoff = 15000
  filterResonance = 1.0

  masterVolume = 0.8
  drumVolume = 0.8
  synthVolume = 0.7

  isRecordingSeq = false

  // ---- Live playback ----
  isPlaying = false
  /** The step currently *audible*, kept in sync with playback by drawLoop — this is what the UI/recording read. */
  currentStep = 0

  private ctx: AudioContext | null = null
  private graph: Graph | null = null
  private drumBufferCache = new Map<string, AudioBuffer>()

  private lookaheadMs = 25
  private scheduleAheadSec = 0.1
  private nextStepTime = 0
  /** How far ahead the scheduler has scheduled audio; intentionally separate from `currentStep`
   *  (the audible step) since scheduling always runs ahead of what's actually sounding. */
  private schedulerCursor = 0
  private timerId: number | null = null
  private stepQueue: { step: number; time: number }[] = []
  private rafId: number | null = null

  private stepListeners = new Set<(step: number) => void>()
  private playbackListeners = new Set<(playing: boolean) => void>()
  private soundPackListeners = new Set<(name: string) => void>()

  get totalSteps(): number {
    return this.barCount * 16
  }

  // ---- Event subscription ----
  onStepChange(cb: (step: number) => void): () => void {
    this.stepListeners.add(cb)
    return () => this.stepListeners.delete(cb)
  }

  onPlaybackChange(cb: (playing: boolean) => void): () => void {
    this.playbackListeners.add(cb)
    return () => this.playbackListeners.delete(cb)
  }

  onSoundPackChange(cb: (name: string) => void): () => void {
    this.soundPackListeners.add(cb)
    return () => this.soundPackListeners.delete(cb)
  }

  // ---- Live audio context (created lazily on first user gesture) ----
  private ensureContext(): AudioContext {
    if (!this.ctx) {
      const AudioContextCtor = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
      this.ctx = new AudioContextCtor({ latencyHint: 'interactive' })
      this.graph = buildGraph(this.ctx, this.ctx.destination, {
        masterVolume: this.masterVolume,
        drumVolume: this.drumVolume,
        synthVolume: this.synthVolume,
        filterCutoff: this.filterCutoff,
        filterResonance: this.filterResonance
      })
    }
    if (this.ctx.state === 'suspended') void this.ctx.resume()
    return this.ctx
  }

  setMasterVolume(v: number) {
    this.masterVolume = v
    if (this.graph) this.graph.master.gain.value = v
  }

  setDrumVolume(v: number) {
    this.drumVolume = v
    if (this.graph) this.graph.drumBus.gain.value = v
  }

  setSynthVolume(v: number) {
    this.synthVolume = v
    if (this.graph) this.graph.synthBus.gain.value = v
  }

  setFilterCutoff(v: number) {
    this.filterCutoff = v
    if (this.graph) this.graph.filter.frequency.value = v
  }

  setFilterResonance(v: number) {
    this.filterResonance = v
    if (this.graph) this.graph.filter.Q.value = v
  }

  setBpm(v: number) {
    this.bpm = Math.min(200, Math.max(60, v))
  }

  setBarCount(v: number) {
    this.barCount = v
  }

  // ---- Drum buffer cache (shared across live + offline contexts; AudioBuffer isn't context-bound) ----
  private getDrumBuffer(sound: DrumSound): AudioBuffer {
    const p = this.drumVoiceParams[sound]
    const key = `${sound}:${p.tune.toFixed(2)}:${p.decay.toFixed(2)}:${p.tone.toFixed(2)}`
    let cached = this.drumBufferCache.get(key)
    if (!cached) {
      cached = toAudioBuffer(generateDrumSamples(sound, p))
      this.drumBufferCache.set(key, cached)
    }
    return cached
  }

  regenerateDrumVoice(sound: DrumSound) {
    // Cache is keyed by params, so nothing to invalidate explicitly — just warms the new entry.
    this.getDrumBuffer(sound)
  }

  updateDrumVoiceParams(sound: DrumSound, params: DrumVoiceParams) {
    this.drumVoiceParams[sound] = params
    this.getDrumBuffer(sound)
  }

  applySoundPack(pack: SoundPack) {
    for (const sound of DRUM_SOUNDS) {
      this.drumVoiceParams[sound] = pack.voiceParams[sound] ?? defaultVoiceParams()
      this.getDrumBuffer(sound)
    }
    this.activeSoundPackName = pack.name
    this.soundPackListeners.forEach((cb) => cb(pack.name))
  }

  // ---- Pattern editing ----
  toggleDrumStep(sound: DrumSound, step: number) {
    this.drumGrid[sound][step] = !this.drumGrid[sound][step]
  }

  isDrumStepActive(sound: DrumSound, step: number): boolean {
    return this.drumGrid[sound][step]
  }

  toggleDrumMute(sound: DrumSound) {
    this.drumMuted[sound] = !this.drumMuted[sound]
  }

  isDrumMuted(sound: DrumSound): boolean {
    return this.drumMuted[sound]
  }

  randomizeDrumTrack(sound: DrumSound, density = 0.35) {
    const total = this.totalSteps
    for (let i = 0; i < total; i++) this.drumGrid[sound][i] = Math.random() < density
  }

  clearDrumTrack(sound: DrumSound) {
    const total = this.totalSteps
    for (let i = 0; i < total; i++) this.drumGrid[sound][i] = false
  }

  duplicateLastBar() {
    if (this.barCount <= 1) return
    const bar = 16
    const lastStart = (this.barCount - 1) * bar
    const prevStart = (this.barCount - 2) * bar
    for (const sound of DRUM_SOUNDS) {
      const grid = this.drumGrid[sound]
      for (let i = 0; i < bar; i++) grid[lastStart + i] = grid[prevStart + i]
    }
    for (let i = 0; i < bar; i++) {
      const src = this.synthSequence.get(prevStart + i)
      if (src) this.synthSequence.set(lastStart + i, src.map((n) => ({ ...n })))
      else this.synthSequence.delete(lastStart + i)
    }
  }

  toggleSynthStep(step: number, pitch: number): boolean {
    const list = this.synthSequence.get(step) ?? []
    const idx = list.findIndex((n) => n.pitch === pitch)
    if (idx !== -1) {
      list.splice(idx, 1)
      this.synthSequence.set(step, list)
      return false
    }
    list.push({ pitch, velocity: 1.0 })
    this.synthSequence.set(step, list)
    return true
  }

  isSynthStepActive(step: number, pitch: number): boolean {
    return this.synthSequence.get(step)?.some((n) => n.pitch === pitch) ?? false
  }

  clearSequencer() {
    this.drumGrid = makeEmptyDrumGrid()
    this.synthSequence.clear()
  }

  // ---- Live triggers ----
  triggerDrumPad(sound: DrumSound, velocity = 1.0) {
    const ctx = this.ensureContext()
    const buffer = this.getDrumBuffer(sound)
    scheduleDrumHit(ctx, this.graph!.drumBus, buffer, ctx.currentTime, velocity)

    if (this.isRecordingSeq && this.isPlaying) {
      this.drumGrid[sound][this.currentStep] = true
    }
  }

  triggerSynthPreview(pitch: number, velocity = 1.0) {
    const ctx = this.ensureContext()
    scheduleSynthNote(ctx, this.graph!.synthBus, this.activeWaveform, this.adsr, ctx.currentTime, pitch, velocity, SYNTH_HOLD_SECONDS)

    if (this.isRecordingSeq && this.isPlaying) {
      const list = this.synthSequence.get(this.currentStep) ?? []
      if (!list.some((n) => n.pitch === pitch)) {
        list.push({ pitch, velocity })
        this.synthSequence.set(this.currentStep, list)
      }
    }
  }

  // ---- Transport / scheduler ----
  togglePlayback() {
    if (this.isPlaying) this.stop()
    else this.start()
  }

  start() {
    if (this.isPlaying) return
    const ctx = this.ensureContext()
    this.isPlaying = true
    this.currentStep = 0
    this.schedulerCursor = 0
    this.stepQueue = []
    this.nextStepTime = ctx.currentTime + 0.05
    this.playbackListeners.forEach((cb) => cb(true))

    this.scheduler()
    this.timerId = window.setInterval(() => this.scheduler(), this.lookaheadMs)
    this.rafId = requestAnimationFrame(this.drawLoop)
  }

  stop() {
    this.isPlaying = false
    this.isRecordingSeq = false
    if (this.timerId != null) {
      clearInterval(this.timerId)
      this.timerId = null
    }
    if (this.rafId != null) {
      cancelAnimationFrame(this.rafId)
      this.rafId = null
    }
    this.playbackListeners.forEach((cb) => cb(false))
  }

  toggleRecording() {
    this.isRecordingSeq = !this.isRecordingSeq
    if (this.isRecordingSeq && !this.isPlaying) this.start()
  }

  private scheduler = () => {
    const ctx = this.ctx!
    while (this.nextStepTime < ctx.currentTime + this.scheduleAheadSec) {
      this.scheduleStep(this.schedulerCursor, this.nextStepTime)
      this.stepQueue.push({ step: this.schedulerCursor, time: this.nextStepTime })
      const secondsPerStep = 60.0 / (this.bpm * 4)
      this.nextStepTime += secondsPerStep
      this.schedulerCursor = (this.schedulerCursor + 1) % this.totalSteps
    }
  }

  private scheduleStep(step: number, time: number) {
    const ctx = this.ctx!
    const graph = this.graph!
    for (const sound of DRUM_SOUNDS) {
      if (!this.drumMuted[sound] && this.drumGrid[sound][step]) {
        scheduleDrumHit(ctx, graph.drumBus, this.getDrumBuffer(sound), time, 1.0)
      }
    }
    const notes = this.synthSequence.get(step)
    if (notes) {
      for (const { pitch, velocity } of notes) {
        scheduleSynthNote(ctx, graph.synthBus, this.activeWaveform, this.adsr, time, pitch, velocity, SYNTH_HOLD_SECONDS)
      }
    }
  }

  private drawLoop = () => {
    if (!this.isPlaying || !this.ctx) return
    const now = this.ctx.currentTime
    while (this.stepQueue.length && this.stepQueue[0].time <= now) {
      const next = this.stepQueue.shift()!
      this.currentStep = next.step
      this.stepListeners.forEach((cb) => cb(next.step))
    }
    this.rafId = requestAnimationFrame(this.drawLoop)
  }

  // ---- Offline export ----
  private async renderPattern(includeDrums: boolean, includeSynth: boolean): Promise<AudioBuffer> {
    const totalFrames = Math.ceil((60 / (this.bpm * 4)) * this.totalSteps * SAMPLE_RATE) + SAMPLE_RATE // +1s tail for release
    const offline = new OfflineAudioContext(2, totalFrames, SAMPLE_RATE)
    const graph = buildGraph(offline, offline.destination, {
      masterVolume: this.masterVolume,
      drumVolume: this.drumVolume,
      synthVolume: this.synthVolume,
      filterCutoff: this.filterCutoff,
      filterResonance: this.filterResonance
    })

    const secondsPerStep = 60.0 / (this.bpm * 4)
    for (let step = 0; step < this.totalSteps; step++) {
      const time = step * secondsPerStep
      if (includeDrums) {
        for (const sound of DRUM_SOUNDS) {
          if (!this.drumMuted[sound] && this.drumGrid[sound][step]) {
            scheduleDrumHit(offline, graph.drumBus, this.getDrumBuffer(sound), time, 1.0)
          }
        }
      }
      if (includeSynth) {
        const notes = this.synthSequence.get(step)
        if (notes) {
          for (const { pitch, velocity } of notes) {
            scheduleSynthNote(offline, graph.synthBus, this.activeWaveform, this.adsr, time, pitch, velocity, SYNTH_HOLD_SECONDS)
          }
        }
      }
    }

    return offline.startRendering()
  }

  async exportWavBlob(): Promise<Blob> {
    const buffer = await this.renderPattern(true, true)
    return audioBufferToWav(buffer)
  }

  async exportStemBlobs(): Promise<StemBlob[]> {
    const [drums, synth] = await Promise.all([this.renderPattern(true, false), this.renderPattern(false, true)])
    return [
      { label: 'Drums', blob: audioBufferToWav(drums) },
      { label: 'Synth', blob: audioBufferToWav(synth) }
    ]
  }

  exportMidiBlob(): Blob {
    return exportMidiFile(this.bpm, this.totalSteps, this.drumGrid, this.drumMuted, this.synthSequence)
  }

  applySynthPatch(patch: SynthPatch) {
    if (WAVEFORMS.includes(patch.waveform as Waveform)) this.activeWaveform = patch.waveform as Waveform
    this.adsr = { attack: patch.attack, decay: patch.decay, sustain: patch.sustain, release: patch.release }
    this.setFilterCutoff(patch.filterCutoff)
    this.setFilterResonance(patch.filterResonance)
  }

  currentSynthPatch(): SynthPatch {
    return {
      waveform: this.activeWaveform,
      attack: this.adsr.attack,
      decay: this.adsr.decay,
      sustain: this.adsr.sustain,
      release: this.adsr.release,
      filterCutoff: this.filterCutoff,
      filterResonance: this.filterResonance
    }
  }
}

export const engine = new Engine()
