import type { SoundPack, SynthPatch } from './audio/soundPacks'

export type PresetType = 'SOUND_PACK' | 'SYNTH_PATCH'

export interface PresetEntity {
  id: string
  name: string
  author: string
  type: PresetType
  payloadJson: string
  createdAt: number
}

const STORAGE_KEY = 'beatcraft.presets.v1'
const listeners = new Set<() => void>()

function readAll(): PresetEntity[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as PresetEntity[]) : []
  } catch {
    return []
  }
}

function writeAll(entities: PresetEntity[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(entities))
  listeners.forEach((cb) => cb())
}

export function subscribe(cb: () => void): () => void {
  listeners.add(cb)
  return () => listeners.delete(cb)
}

export function getAllPresets(): PresetEntity[] {
  return readAll().sort((a, b) => b.createdAt - a.createdAt)
}

export function getPresetsByType(type: PresetType): PresetEntity[] {
  return getAllPresets().filter((p) => p.type === type)
}

function save(type: PresetType, name: string, author: string, payloadJson: string): PresetEntity {
  const entity: PresetEntity = {
    id: `${type}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    name,
    author: author.trim() || 'Anonymous',
    type,
    payloadJson,
    createdAt: Date.now()
  }
  writeAll([...readAll(), entity])
  return entity
}

export function saveSoundPack(pack: SoundPack, author: string): PresetEntity {
  return save('SOUND_PACK', pack.name, author, JSON.stringify(pack))
}

export function saveSynthPatch(name: string, author: string, patch: SynthPatch): PresetEntity {
  return save('SYNTH_PATCH', name, author, JSON.stringify(patch))
}

export function decodeSoundPack(entity: PresetEntity): SoundPack | null {
  if (entity.type !== 'SOUND_PACK') return null
  try {
    return JSON.parse(entity.payloadJson) as SoundPack
  } catch {
    return null
  }
}

export function decodeSynthPatch(entity: PresetEntity): SynthPatch | null {
  if (entity.type !== 'SYNTH_PATCH') return null
  try {
    return JSON.parse(entity.payloadJson) as SynthPatch
  } catch {
    return null
  }
}

export function parseSoundPackJson(json: string): SoundPack | null {
  try {
    const pack = JSON.parse(json) as SoundPack
    return pack && pack.name && pack.voiceParams ? pack : null
  } catch {
    return null
  }
}

export function parseSynthPatchJson(json: string): SynthPatch | null {
  try {
    const patch = JSON.parse(json) as SynthPatch
    return patch && typeof patch.attack === 'number' ? patch : null
  } catch {
    return null
  }
}

export function importSoundPackJson(json: string): PresetEntity | null {
  const pack = parseSoundPackJson(json)
  if (!pack) return null
  return saveSoundPack(pack, pack.author ?? 'Community')
}

export function importSynthPatchJson(json: string): PresetEntity | null {
  const patch = parseSynthPatchJson(json)
  if (!patch) return null
  return saveSynthPatch('Imported Preset', 'Community', patch)
}

export function deletePreset(id: string) {
  writeAll(readAll().filter((p) => p.id !== id))
}
