import { SCALE_TYPES, isInScale, type ScaleType } from './audio/musicTheory'

/** Melody-assistance state: which key/scale to highlight, and whether to enforce it. */
let scaleRoot = 0 // 0 = C .. 11 = B
let scaleType: ScaleType = SCALE_TYPES[0]
let scaleLockEnabled = false

const listeners = new Set<() => void>()

function notify() {
  listeners.forEach((cb) => cb())
}

export function subscribeMelodyState(cb: () => void): () => void {
  listeners.add(cb)
  return () => listeners.delete(cb)
}

export function getScaleRoot(): number {
  return scaleRoot
}

export function setScaleRoot(root: number) {
  scaleRoot = root
  notify()
}

export function getScaleType(): ScaleType {
  return scaleType
}

export function setScaleType(type: ScaleType) {
  scaleType = type
  notify()
}

export function isScaleLockEnabled(): boolean {
  return scaleLockEnabled
}

export function toggleScaleLock() {
  scaleLockEnabled = !scaleLockEnabled
  notify()
}

export function isNoteInScale(note: number): boolean {
  return isInScale(scaleRoot, scaleType, note)
}
