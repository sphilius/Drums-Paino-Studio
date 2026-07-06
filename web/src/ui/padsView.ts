import { engine } from '../audio/engine'
import { DRUM_DISPLAY_NAMES, DRUM_SOUNDS, type DrumSound } from '../audio/soundPacks'
import { h, clear } from './dom'
import { openSoundDesignDialog } from './soundDesignDialog'

const PAD_COLOR: Record<DrumSound, string> = {
  KICK: 'var(--pink)',
  SNARE: 'var(--blue)',
  HIHAT_CLOSED: 'var(--green)',
  HIHAT_OPEN: 'var(--green)',
  CLAP: 'var(--orange)',
  TOM: 'var(--orange)'
}

export function mountPadsView(container: HTMLElement): () => void {
  let velocity = 1.0

  const velocityLabel = h('span', {}, [`PAD VELOCITY: ${Math.round(velocity * 100)}%`])

  const grid = h(
    'div',
    { class: 'pads-grid' },
    DRUM_SOUNDS.map((sound, index) => {
      const pad = h(
        'div',
        { class: 'pad' },
        [
          h('div', { class: 'pad-dot', style: `background:${PAD_COLOR[sound]}` }),
          h('div', { class: 'pad-name' }, [DRUM_DISPLAY_NAMES[sound]]),
          h('div', { class: 'pad-index' }, [`PAD ${index + 1}`]),
          h('button', {
            class: 'tune-btn',
            onclick: (e: Event) => {
              e.stopPropagation()
              openSoundDesignDialog(sound)
            }
          }, ['\u{1F39B}'])
        ]
      )
      const press = () => pad.classList.add('pressed')
      const release = () => pad.classList.remove('pressed')
      pad.addEventListener('pointerdown', () => {
        press()
        engine.triggerDrumPad(sound, velocity)
        if (navigator.vibrate) navigator.vibrate(10)
      })
      pad.addEventListener('pointerup', release)
      pad.addEventListener('pointercancel', release)
      pad.addEventListener('pointerleave', release)
      return pad
    })
  )

  const velocitySlider = h('input', {
    type: 'range',
    min: '0.1',
    max: '1',
    step: '0.01',
    value: String(velocity),
    oninput: (e: Event) => {
      velocity = Number((e.target as HTMLInputElement).value)
      velocityLabel.textContent = `PAD VELOCITY: ${Math.round(velocity * 100)}%`
    }
  })

  clear(container)
  container.append(
    h('div', {}, [
      h('div', { class: 'section-title' }, ['MPC LIVE PERFORMANCE PADS']),
      grid,
      h('div', { class: 'card', style: 'margin-top:16px;display:flex;align-items:center;gap:10px' }, [
        h('span', { style: 'font-size:16px' }, ['\u{1F30A}']),
        velocityLabel,
        velocitySlider
      ]),
      h('p', { class: 'hint' }, ['Tap a pad to play it. Tap the \u{1F39B} icon to sound-design that pad live.'])
    ])
  )

  return () => {
    /* no engine subscriptions held by this view */
  }
}
