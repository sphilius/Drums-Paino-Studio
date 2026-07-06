import type { DrumSound } from './soundPacks'
import { DRUM_SOUNDS } from './soundPacks'

/** Minimal Standard MIDI File (format 0) writer, ported from MidiFileExporter.kt. */

const TICKS_PER_QUARTER = 96
const DRUM_CHANNEL = 9 // MIDI channel 10 (General MIDI percussion)
const SYNTH_CHANNEL = 0

const DRUM_MIDI_NOTES: Record<DrumSound, number> = {
  KICK: 36,
  SNARE: 38,
  HIHAT_CLOSED: 42,
  HIHAT_OPEN: 46,
  CLAP: 39,
  TOM: 45
}

interface MidiEvent {
  tick: number
  isNoteOn: boolean
  channel: number
  note: number
  velocity: number
}

export interface SynthNote {
  pitch: number
  velocity: number
}

export function exportMidiFile(
  bpm: number,
  totalSteps: number,
  drumGrid: Record<DrumSound, boolean[]>,
  drumMuted: Record<DrumSound, boolean>,
  synthSequence: Map<number, SynthNote[]>
): Blob {
  const ticksPerStep = TICKS_PER_QUARTER / 4 // sequencer steps are 16th notes
  const events: MidiEvent[] = []

  for (let step = 0; step < totalSteps; step++) {
    const tick = step * ticksPerStep

    for (const sound of DRUM_SOUNDS) {
      if (!drumMuted[sound] && drumGrid[sound][step]) {
        const note = DRUM_MIDI_NOTES[sound]
        events.push({ tick, isNoteOn: true, channel: DRUM_CHANNEL, note, velocity: 100 })
        events.push({ tick: tick + ticksPerStep / 2, isNoteOn: false, channel: DRUM_CHANNEL, note, velocity: 0 })
      }
    }

    const notes = synthSequence.get(step)
    if (notes) {
      for (const { pitch, velocity } of notes) {
        const vel = Math.min(127, Math.max(1, Math.round(velocity * 127)))
        events.push({ tick, isNoteOn: true, channel: SYNTH_CHANNEL, note: pitch, velocity: vel })
        events.push({
          tick: tick + Math.max(1, ticksPerStep - 2),
          isNoteOn: false,
          channel: SYNTH_CHANNEL,
          note: pitch,
          velocity: 0
        })
      }
    }
  }

  events.sort((a, b) => a.tick - b.tick || Number(a.isNoteOn) - Number(b.isNoteOn))

  const header = headerChunk()
  const track = trackChunk(buildTrackBytes(bpm, events))
  const bytes = new Uint8Array(header.length + track.length)
  bytes.set(header, 0)
  bytes.set(track, header.length)
  return new Blob([bytes], { type: 'audio/midi' })
}

function headerChunk(): number[] {
  return [
    ...str('MThd'),
    0, 0, 0, 6,
    0, 0, // format 0
    0, 1, // 1 track
    (TICKS_PER_QUARTER >> 8) & 0xff,
    TICKS_PER_QUARTER & 0xff
  ]
}

function trackChunk(data: number[]): number[] {
  const len = data.length
  return [
    ...str('MTrk'),
    (len >> 24) & 0xff,
    (len >> 16) & 0xff,
    (len >> 8) & 0xff,
    len & 0xff,
    ...data
  ]
}

function buildTrackBytes(bpm: number, events: MidiEvent[]): number[] {
  const out: number[] = []

  const microsPerQuarter = Math.floor(60_000_000 / Math.min(300, Math.max(1, bpm)))
  out.push(0) // delta time
  out.push(0xff, 0x51, 0x03)
  out.push((microsPerQuarter >> 16) & 0xff, (microsPerQuarter >> 8) & 0xff, microsPerQuarter & 0xff)

  let lastTick = 0
  for (const event of events) {
    const delta = Math.max(0, event.tick - lastTick)
    lastTick = event.tick
    out.push(...encodeVarLen(delta))

    const statusByte = (event.isNoteOn ? 0x90 : 0x80) | event.channel
    out.push(statusByte, event.note & 0x7f, event.velocity & 0x7f)
  }

  out.push(0, 0xff, 0x2f, 0x00) // end of track

  return out
}

/** Standard MIDI variable-length quantity encoding (7 bits per byte, MSB continuation flag). */
function encodeVarLen(value: number): number[] {
  let v = value
  const groups: number[] = [v & 0x7f]
  v = Math.floor(v / 128)
  while (v > 0) {
    groups.push((v & 0x7f) | 0x80)
    v = Math.floor(v / 128)
  }
  return groups.reverse()
}

function str(s: string): number[] {
  return Array.from(s).map((c) => c.charCodeAt(0))
}
