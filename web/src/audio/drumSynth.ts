import type { DrumSound, DrumVoiceParams } from './soundPacks'

export const SAMPLE_RATE = 44100

/** Procedural drum synthesis, ported 1:1 from the Kotlin AudioEngine generators. */

function kick(tune: number, decay: number, tone: number): Float32Array {
  const pitchMul = Math.pow(2, tune / 12)
  const len = Math.max(64, Math.floor(SAMPLE_RATE * 0.25 * decay))
  const arr = new Float32Array(len)
  for (let i = 0; i < len; i++) {
    const t = i / SAMPLE_RATE
    const freq = (140 * Math.exp((-28 * t) / decay) + 40) * pitchMul
    const amp = Math.exp((-10 * t) / decay)
    const click = t < 0.004 ? (1 - t / 0.004) * tone : 0
    arr[i] = (Math.sin(2 * Math.PI * freq * t) * (1 - tone * 0.3) + click) * amp
  }
  return arr
}

function snare(tune: number, decay: number, tone: number): Float32Array {
  const bodyFreq = 180 * Math.pow(2, tune / 12)
  const len = Math.max(64, Math.floor(SAMPLE_RATE * 0.18 * decay))
  const arr = new Float32Array(len)
  for (let i = 0; i < len; i++) {
    const t = i / SAMPLE_RATE
    const toneComponent = Math.sin(2 * Math.PI * bodyFreq * t) * Math.exp((-25 * t) / decay)
    const noiseComponent = (Math.random() * 2 - 1) * Math.exp((-14 * t) / decay)
    arr[i] = toneComponent * tone * 0.8 + noiseComponent * (1 - tone) * 0.9
  }
  return arr
}

function hihat(decaySec: number, decayMul: number, tone: number): Float32Array {
  const effectiveDecay = decaySec * decayMul
  const len = Math.max(32, Math.floor(SAMPLE_RATE * effectiveDecay))
  const arr = new Float32Array(len)
  let lastVal = 0
  const filterStrength = 0.4 + tone * 1.2
  for (let i = 0; i < len; i++) {
    const t = i / SAMPLE_RATE
    const rawNoise = Math.random() * 2 - 1
    const filtered = rawNoise - lastVal * filterStrength
    lastVal = rawNoise
    arr[i] = filtered * Math.exp(-t / (effectiveDecay * 0.4)) * 0.35
  }
  return arr
}

function clap(decay: number, tone: number): Float32Array {
  const len = Math.max(64, Math.floor(SAMPLE_RATE * 0.22 * decay))
  const arr = new Float32Array(len)
  const burst = 0.01 * decay
  for (let i = 0; i < len; i++) {
    const t = i / SAMPLE_RATE
    let amp: number
    if (t < burst) amp = Math.exp((-150 * t) / decay) * 0.4
    else if (t < burst * 2) amp = Math.exp((-150 * (t - burst)) / decay) * 0.5
    else if (t < burst * 3) amp = Math.exp((-150 * (t - burst * 2)) / decay) * 0.6
    else amp = Math.exp((-16 * (t - burst * 3)) / decay) * (0.6 + tone * 0.4)
    const noiseVal = (Math.random() * 2 - 1) * amp
    arr[i] = noiseVal * 0.5
  }
  return arr
}

function tom(tune: number, decay: number, tone: number): Float32Array {
  const pitchMul = Math.pow(2, tune / 12)
  const len = Math.max(64, Math.floor(SAMPLE_RATE * 0.3 * decay))
  const arr = new Float32Array(len)
  for (let i = 0; i < len; i++) {
    const t = i / SAMPLE_RATE
    const freq = (120 * Math.exp((-12 * t) / decay) + 55) * pitchMul
    const amp = Math.exp((-6 * t) / decay)
    const fundamental = Math.sin(2 * Math.PI * freq * t)
    const overtone = Math.sin(2 * Math.PI * freq * 2 * t) * 0.3
    arr[i] = (fundamental * (1 - tone * 0.3) + overtone * tone * 0.3) * amp * 0.6
  }
  return arr
}

export function generateDrumSamples(sound: DrumSound, params: DrumVoiceParams): Float32Array {
  const { tune, decay, tone } = params
  switch (sound) {
    case 'KICK':
      return kick(tune, decay, tone)
    case 'SNARE':
      return snare(tune, decay, tone)
    case 'HIHAT_CLOSED':
      return hihat(0.04, decay, tone)
    case 'HIHAT_OPEN':
      return hihat(0.3, decay, tone)
    case 'CLAP':
      return clap(decay, tone)
    case 'TOM':
      return tom(tune, decay, tone)
  }
}

/**
 * AudioBuffer is not tied to any particular BaseAudioContext, so a single cached
 * buffer can back AudioBufferSourceNodes on both the live AudioContext and any
 * throwaway OfflineAudioContext used for export.
 */
export function toAudioBuffer(samples: Float32Array): AudioBuffer {
  const buffer = new AudioBuffer({ numberOfChannels: 1, length: samples.length, sampleRate: SAMPLE_RATE })
  // `copyToChannel` types its argument as Float32Array<ArrayBuffer>; a freshly allocated
  // Float32Array is always ArrayBuffer-backed (never SharedArrayBuffer) so this cast is safe.
  buffer.copyToChannel(samples as Float32Array<ArrayBuffer>, 0)
  return buffer
}
