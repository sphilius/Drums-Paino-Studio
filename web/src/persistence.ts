import { engine } from './audio/engine'
import { DRUM_SOUNDS, type DrumVoiceParams } from './audio/soundPacks'
import type { SynthNote } from './audio/midiExporter'

const STORAGE_KEY = 'beatcraft.session.v1'

interface SessionSnapshot {
  bpm: number
  barCount: number
  drumGrid: Record<string, boolean[]>
  synthSequence: [number, SynthNote[]][]
  drumVoiceParams: Record<string, DrumVoiceParams>
  drumMuted: Record<string, boolean>
  activeSoundPackName: string
  activeWaveform: string
  adsr: { attack: number; decay: number; sustain: number; release: number }
  filterCutoff: number
  filterResonance: number
  masterVolume: number
  drumVolume: number
  synthVolume: number
}

export function saveSession() {
  const snapshot: SessionSnapshot = {
    bpm: engine.bpm,
    barCount: engine.barCount,
    drumGrid: engine.drumGrid,
    synthSequence: Array.from(engine.synthSequence.entries()),
    drumVoiceParams: engine.drumVoiceParams,
    drumMuted: engine.drumMuted,
    activeSoundPackName: engine.activeSoundPackName,
    activeWaveform: engine.activeWaveform,
    adsr: engine.adsr,
    filterCutoff: engine.filterCutoff,
    filterResonance: engine.filterResonance,
    masterVolume: engine.masterVolume,
    drumVolume: engine.drumVolume,
    synthVolume: engine.synthVolume
  }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot))
  } catch {
    // storage full/unavailable — silently skip autosave
  }
}

export function restoreSession(): boolean {
  let raw: string | null
  try {
    raw = localStorage.getItem(STORAGE_KEY)
  } catch {
    return false
  }
  if (!raw) return false

  try {
    const snapshot = JSON.parse(raw) as SessionSnapshot
    engine.bpm = snapshot.bpm ?? engine.bpm
    engine.barCount = snapshot.barCount ?? engine.barCount
    for (const sound of DRUM_SOUNDS) {
      if (snapshot.drumGrid?.[sound]) engine.drumGrid[sound] = snapshot.drumGrid[sound]
      if (snapshot.drumVoiceParams?.[sound]) engine.drumVoiceParams[sound] = snapshot.drumVoiceParams[sound]
      if (snapshot.drumMuted?.[sound] !== undefined) engine.drumMuted[sound] = snapshot.drumMuted[sound]
    }
    engine.synthSequence.clear()
    for (const [step, notes] of snapshot.synthSequence ?? []) engine.synthSequence.set(step, notes)
    engine.activeSoundPackName = snapshot.activeSoundPackName ?? engine.activeSoundPackName
    if (snapshot.activeWaveform) engine.activeWaveform = snapshot.activeWaveform as typeof engine.activeWaveform
    engine.adsr = snapshot.adsr ?? engine.adsr
    engine.filterCutoff = snapshot.filterCutoff ?? engine.filterCutoff
    engine.filterResonance = snapshot.filterResonance ?? engine.filterResonance
    engine.masterVolume = snapshot.masterVolume ?? engine.masterVolume
    engine.drumVolume = snapshot.drumVolume ?? engine.drumVolume
    engine.synthVolume = snapshot.synthVolume ?? engine.synthVolume
    return true
  } catch {
    return false
  }
}

export function startAutosave() {
  window.setInterval(saveSession, 4000)
  window.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') saveSession()
  })
  window.addEventListener('pagehide', saveSession)
}
