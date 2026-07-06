import { engine } from '../audio/engine'
import { DRUM_DISPLAY_NAMES, DRUM_SOUNDS, type DrumSound } from '../audio/soundPacks'
import { h, clear } from './dom'

export function mountSequencerView(container: HTMLElement): () => void {
  let barCount = engine.barCount
  const totalSteps = () => barCount * 16

  const stepCells: Record<DrumSound, HTMLElement[]> = {} as Record<DrumSound, HTMLElement[]>
  const stepNumCells: HTMLElement[] = []
  let lastStep = -1

  const root = h('div', {})
  clear(container)
  container.append(root)

  function render() {
    for (const arr of Object.values(stepCells)) arr.length = 0
    stepNumCells.length = 0

    const toolbar = h('div', { class: 'seq-toolbar' }, [
      h('div', { class: 'section-title', style: 'margin:0' }, ['DRUM GRID SEQUENCER']),
      h('div', { style: 'display:flex;align-items:center;gap:6px' }, [
        ...[1, 2, 4].map((bars) =>
          h('button', {
            class: `bar-btn${bars === barCount ? ' active' : ''}`,
            onclick: () => {
              barCount = bars
              engine.setBarCount(bars)
              render()
            }
          }, [`${bars}B`])
        ),
        h('button', {
          class: 'icon-btn',
          style: 'width:32px;height:32px;font-size:14px',
          onclick: () => {
            engine.duplicateLastBar()
            render()
          }
        }, ['\u{29C9}'])
      ])
    ])

    const headerRow = h(
      'div',
      { class: 'seq-header-row' },
      Array.from({ length: totalSteps() }, (_, step) => {
        const cell = h('div', { class: `step-num${step % 4 === 0 ? ' beat-marker' : ''}` }, [String(step + 1)])
        stepNumCells.push(cell)
        return cell
      })
    )

    const rows = DRUM_SOUNDS.map((sound) => {
      stepCells[sound] = []
      const muted = engine.isDrumMuted(sound)

      const label = h('div', { class: 'seq-track-label' }, [
        h('button', {
          onclick: () => {
            engine.toggleDrumMute(sound)
            render()
          }
        }, [engine.isDrumMuted(sound) ? '\u{1F507}' : '\u{1F50A}']),
        h('div', { class: 'name', style: muted ? 'color:var(--text-dim)' : '' }, [DRUM_DISPLAY_NAMES[sound]]),
        h('button', {
          onclick: () => {
            engine.randomizeDrumTrack(sound)
            render()
          }
        }, ['\u{1F3B2}'])
      ])

      const cells = Array.from({ length: totalSteps() }, (_, step) => {
        const active = engine.isDrumStepActive(sound, step)
        const cell = h('div', {
          class: `step-cell${active ? ' active' : ''}${step % 4 === 0 ? ' beat-marker' : ''}`,
          onclick: () => {
            engine.toggleDrumStep(sound, step)
            cell.classList.toggle('active')
          }
        })
        stepCells[sound].push(cell)
        return cell
      })

      return h('div', { class: 'seq-row' }, [label, ...cells])
    })

    const table = h('div', { class: 'seq-table' }, [headerRow, ...rows])
    const scroll = h('div', { class: 'seq-scroll' }, [table])

    clear(root)
    root.append(toolbar, scroll)
    lastStep = -1
    highlightStep(engine.currentStep)
  }

  function highlightStep(step: number) {
    if (lastStep >= 0 && lastStep < stepNumCells.length) {
      stepNumCells[lastStep]?.classList.remove('playhead')
      for (const sound of DRUM_SOUNDS) stepCells[sound]?.[lastStep]?.classList.remove('playhead')
    }
    if (step >= 0 && step < stepNumCells.length) {
      stepNumCells[step]?.classList.add('playhead')
      for (const sound of DRUM_SOUNDS) stepCells[sound]?.[step]?.classList.add('playhead')
    }
    lastStep = step
  }

  render()
  const unsubStep = engine.onStepChange((step) => highlightStep(step % totalSteps()))

  return () => {
    unsubStep()
  }
}
