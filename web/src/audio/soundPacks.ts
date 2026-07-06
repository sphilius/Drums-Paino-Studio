export const DRUM_SOUNDS = ['KICK', 'SNARE', 'HIHAT_CLOSED', 'HIHAT_OPEN', 'CLAP', 'TOM'] as const
export type DrumSound = (typeof DRUM_SOUNDS)[number]

export const DRUM_DISPLAY_NAMES: Record<DrumSound, string> = {
  KICK: 'Kick',
  SNARE: 'Snare',
  HIHAT_CLOSED: 'Closed Hat',
  HIHAT_OPEN: 'Open Hat',
  CLAP: 'Clap',
  TOM: 'Tom'
}

export interface DrumVoiceParams {
  tune: number // semitone offset, roughly -12..12
  decay: number // envelope length multiplier, roughly 0.3..2.5
  tone: number // voice-specific timbre blend, 0..1
}

export function defaultVoiceParams(): DrumVoiceParams {
  return { tune: 0, decay: 1, tone: 0.5 }
}

export interface SoundPack {
  id: string
  name: string
  author: string
  description: string
  voiceParams: Record<string, DrumVoiceParams>
}

function pack(id: string, name: string, author: string, description: string, voiceParams: Partial<Record<DrumSound, DrumVoiceParams>>): SoundPack {
  const full: Record<string, DrumVoiceParams> = {}
  for (const sound of DRUM_SOUNDS) {
    full[sound] = voiceParams[sound] ?? defaultVoiceParams()
  }
  return { id, name, author, description, voiceParams: full }
}

export const BUILT_IN_SOUND_PACKS: SoundPack[] = [
  pack('studio_standard', 'Studio Standard', 'BeatCraft', 'Clean, balanced procedural kit for general purpose beatmaking.', {}),
  pack('808_trap_heat', '808 Trap Heat', 'BeatCraft', 'Deep boomy kick, snappy snare and tight bright hats for trap beats.', {
    KICK: { tune: -4, decay: 1.8, tone: 0.7 },
    SNARE: { tune: 2, decay: 0.9, tone: 0.3 },
    HIHAT_CLOSED: { tune: 0, decay: 0.6, tone: 0.8 },
    HIHAT_OPEN: { tune: 0, decay: 1.3, tone: 0.7 },
    CLAP: { tune: 0, decay: 1.1, tone: 0.6 },
    TOM: { tune: -3, decay: 1.4, tone: 0.4 }
  }),
  pack('lofi_dust', 'Lo-Fi Dust', 'BeatCraft', 'Muffled, dusty textures for chill and boom-bap production.', {
    KICK: { tune: -2, decay: 0.8, tone: 0.2 },
    SNARE: { tune: -1, decay: 0.7, tone: 0.65 },
    HIHAT_CLOSED: { tune: 0, decay: 0.5, tone: 0.25 },
    HIHAT_OPEN: { tune: 0, decay: 0.8, tone: 0.25 },
    CLAP: { tune: 0, decay: 0.8, tone: 0.3 },
    TOM: { tune: -2, decay: 0.9, tone: 0.6 }
  }),
  pack('acoustic_room', 'Acoustic Room', 'BeatCraft', 'Roomier, natural-feeling kit with longer tails and airy tone.', {
    KICK: { tune: 1, decay: 1.2, tone: 0.55 },
    SNARE: { tune: 0, decay: 1.3, tone: 0.55 },
    HIHAT_CLOSED: { tune: 0, decay: 1.0, tone: 0.55 },
    HIHAT_OPEN: { tune: 0, decay: 1.5, tone: 0.6 },
    CLAP: { tune: 0, decay: 1.4, tone: 0.55 },
    TOM: { tune: 2, decay: 1.6, tone: 0.5 }
  })
]

export interface SynthPatch {
  waveform: string
  attack: number
  decay: number
  sustain: number
  release: number
  filterCutoff: number
  filterResonance: number
}
