export interface ScaleType {
  id: string
  displayName: string
  intervals: number[]
}

export const SCALE_TYPES: ScaleType[] = [
  { id: 'MAJOR', displayName: 'Major', intervals: [0, 2, 4, 5, 7, 9, 11] },
  { id: 'NATURAL_MINOR', displayName: 'Minor', intervals: [0, 2, 3, 5, 7, 8, 10] },
  { id: 'DORIAN', displayName: 'Dorian', intervals: [0, 2, 3, 5, 7, 9, 10] },
  { id: 'MIXOLYDIAN', displayName: 'Mixolydian', intervals: [0, 2, 4, 5, 7, 9, 10] },
  { id: 'MAJOR_PENTATONIC', displayName: 'Major Pent.', intervals: [0, 2, 4, 7, 9] },
  { id: 'MINOR_PENTATONIC', displayName: 'Minor Pent.', intervals: [0, 3, 5, 7, 10] },
  { id: 'BLUES', displayName: 'Blues', intervals: [0, 3, 5, 6, 7, 10] },
  { id: 'CHROMATIC', displayName: 'Chromatic', intervals: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11] }
]

export const PITCH_CLASS_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']

export function noteName(midiNote: number): string {
  const octave = Math.floor(midiNote / 12) - 1
  const pc = ((midiNote % 12) + 12) % 12
  return `${PITCH_CLASS_NAMES[pc]}${octave}`
}

export function isBlackKey(midiNote: number): boolean {
  const pc = ((midiNote % 12) + 12) % 12
  return PITCH_CLASS_NAMES[pc].includes('#')
}

export function isInScale(rootPitchClass: number, scale: ScaleType, midiNote: number): boolean {
  const degree = (((midiNote - rootPitchClass) % 12) + 12) % 12
  return scale.intervals.includes(degree)
}
