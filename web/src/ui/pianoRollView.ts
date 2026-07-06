import { engine, WAVEFORMS } from '../audio/engine'
import { PITCH_CLASS_NAMES, SCALE_TYPES, isBlackKey, noteName } from '../audio/musicTheory'
import {
  getScaleRoot,
  getScaleType,
  isNoteInScale,
  isScaleLockEnabled,
  setScaleRoot,
  setScaleType,
  subscribeMelodyState,
  toggleScaleLock
} from '../melodyState'
import { h, clear } from './dom'

const NOTES = Array.from({ length: 13 }, (_, i) => 72 - i) // C5 downTo C4

export function mountPianoRollView(container: HTMLElement): () => void {
  const totalSteps = () => engine.totalSteps

  const cellsByNote = new Map<number, HTMLElement[]>()
  const headerCells: HTMLElement[] = []
  let lastStep = -1

  const root = h('div', {})
  clear(container)
  container.append(root)

  function toggleCell(step: number, note: number, cell: HTMLElement) {
    const alreadyActive = engine.isSynthStepActive(step, note)
    if (!alreadyActive && isScaleLockEnabled() && !isNoteInScale(note)) return
    const nowActive = engine.toggleSynthStep(step, note)
    cell.classList.toggle('active', nowActive)
  }

  function renderWaveformRow(): HTMLElement {
    return h('div', { class: 'melody-toolbar' }, [
      h('div', { class: 'section-title', style: 'margin:0' }, ['OSC WAVEFORM']),
      h(
        'div',
        { style: 'display:flex;gap:4px;flex-wrap:wrap' },
        WAVEFORMS.map((wave) =>
          h('button', {
            class: `bar-btn${engine.activeWaveform === wave ? ' active' : ''}`,
            onclick: () => {
              engine.activeWaveform = wave
              rerenderToolbars()
            }
          }, [wave])
        )
      )
    ])
  }

  function renderMelodyToolbar(): HTMLElement {
    const keySelect = h(
      'select',
      {
        onchange: (e: Event) => setScaleRoot(Number((e.target as HTMLSelectElement).value))
      },
      PITCH_CLASS_NAMES.map((name, i) => h('option', { value: String(i), selected: i === getScaleRoot() }, [name]))
    )

    const scaleSelect = h(
      'select',
      {
        onchange: (e: Event) => {
          const type = SCALE_TYPES.find((s) => s.id === (e.target as HTMLSelectElement).value)
          if (type) setScaleType(type)
        }
      },
      SCALE_TYPES.map((s) => h('option', { value: s.id, selected: s.id === getScaleType().id }, [s.displayName]))
    )

    const lockToggle = h('input', {
      type: 'checkbox',
      onchange: () => toggleScaleLock()
    })
    lockToggle.checked = isScaleLockEnabled()

    return h('div', { class: 'melody-toolbar' }, [
      h('div', {}, [h('label', { style: 'margin:0 6px 0 0;display:inline' }, ['Key']), keySelect]),
      h('div', {}, [h('label', { style: 'margin:0 6px 0 0;display:inline' }, ['Scale']), scaleSelect]),
      h('div', { class: 'switch-row' }, [h('span', {}, ['Scale Lock']), lockToggle])
    ])
  }

  function adsrDial(label: string, value: number, min: number, max: number, step: number, onChange: (v: number) => void) {
    const valueLabel = h('span', {}, [value.toFixed(2)])
    const slider = h('input', {
      type: 'range',
      min: String(min),
      max: String(max),
      step: String(step),
      value: String(value),
      oninput: (e: Event) => {
        const v = Number((e.target as HTMLInputElement).value)
        valueLabel.textContent = v.toFixed(2)
        onChange(v)
      }
    })
    return h('div', { style: 'flex:1;min-width:70px' }, [
      h('div', { style: 'font-size:9px;color:var(--text-dim);font-weight:700' }, [label]),
      slider,
      valueLabel
    ])
  }

  function renderAdsrCard(): HTMLElement {
    return h('div', { class: 'card', style: 'margin-top:12px' }, [
      h('div', { class: 'section-title', style: 'font-size:10px' }, ['SYNTH ENVELOPE & FILTER']),
      h('div', { style: 'display:flex;gap:8px;flex-wrap:wrap' }, [
        adsrDial('Attack', engine.adsr.attack, 0.01, 1.0, 0.01, (v) => (engine.adsr.attack = v)),
        adsrDial('Decay', engine.adsr.decay, 0.05, 1.5, 0.01, (v) => (engine.adsr.decay = v)),
        adsrDial('Sustain', engine.adsr.sustain, 0.0, 1.0, 0.01, (v) => (engine.adsr.sustain = v)),
        adsrDial('Release', engine.adsr.release, 0.05, 2.0, 0.01, (v) => (engine.adsr.release = v)),
        adsrDial('Cutoff', engine.filterCutoff, 200, 18000, 50, (v) => engine.setFilterCutoff(v)),
        adsrDial('Resonance', engine.filterResonance, 0.5, 5.0, 0.1, (v) => engine.setFilterResonance(v))
      ])
    ])
  }

  let waveformRowEl: HTMLElement
  let melodyToolbarEl: HTMLElement
  let gridEl: HTMLElement
  let adsrEl: HTMLElement

  function rerenderToolbars() {
    const fresh = renderWaveformRow()
    waveformRowEl.replaceWith(fresh)
    waveformRowEl = fresh
  }

  function renderGrid() {
    cellsByNote.clear()
    headerCells.length = 0

    for (let step = 0; step < totalSteps(); step++) {
      headerCells.push(
        h(
          'div',
          {
            class: 'piano-cell',
            style: 'background:transparent;border:none;display:flex;align-items:center;justify-content:center;font-size:9px;color:var(--text-dim)'
          },
          [String(step + 1)]
        )
      )
    }

    const keysCol = h(
      'div',
      { class: 'piano-keys' },
      NOTES.map((note) =>
        h(
          'div',
          {
            class: `piano-key ${isBlackKey(note) ? 'black' : 'white'}${isNoteInScale(note) ? ' in-scale' : ''}`,
            onpointerdown: () => engine.triggerSynthPreview(note)
          },
          [noteName(note)]
        )
      )
    )

    const gridRows = NOTES.map((note) => {
      const inScale = isNoteInScale(note)
      const rowCells = Array.from({ length: totalSteps() }, (_, step) => {
        const active = engine.isSynthStepActive(step, note)
        const cell = h('div', {
          class: `piano-cell${active ? ' active' : ''}${inScale ? ' in-scale' : ''}`,
          onclick: () => toggleCell(step, note, cell)
        })
        return cell
      })
      cellsByNote.set(note, rowCells)
      return h('div', { class: 'piano-row' }, rowCells)
    })

    const gridScroll = h('div', { class: 'piano-grid-scroll' }, [
      h('div', {}, [h('div', { class: 'piano-row' }, headerCells), ...gridRows])
    ])

    const roll = h('div', { class: 'piano-roll' }, [keysCol, gridScroll])

    const fresh = h('div', {}, [roll])
    gridEl.replaceWith(fresh)
    gridEl = fresh
    lastStep = -1
    highlightStep(engine.currentStep)
  }

  function highlightStep(step: number) {
    if (lastStep >= 0) {
      headerCells[lastStep]?.classList.remove('playhead')
      for (const cells of cellsByNote.values()) cells[lastStep]?.classList.remove('playhead')
    }
    if (step >= 0 && step < headerCells.length) {
      headerCells[step]?.classList.add('playhead')
      for (const cells of cellsByNote.values()) cells[step]?.classList.add('playhead')
    }
    lastStep = step
  }

  waveformRowEl = renderWaveformRow()
  melodyToolbarEl = renderMelodyToolbar()
  gridEl = h('div', {})
  adsrEl = renderAdsrCard()

  root.append(waveformRowEl, melodyToolbarEl, gridEl, adsrEl)
  renderGrid()

  const unsubStep = engine.onStepChange((step) => highlightStep(step % totalSteps()))
  const unsubMelody = subscribeMelodyState(() => {
    const freshToolbar = renderMelodyToolbar()
    melodyToolbarEl.replaceWith(freshToolbar)
    melodyToolbarEl = freshToolbar
    renderGrid()
  })

  return () => {
    unsubStep()
    unsubMelody()
  }
}
