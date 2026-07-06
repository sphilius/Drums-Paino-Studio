import { engine } from '../audio/engine'
import { DRUM_DISPLAY_NAMES, type DrumSound } from '../audio/soundPacks'
import { h } from './dom'

export function openSoundDesignDialog(sound: DrumSound) {
  const initial = engine.drumVoiceParams[sound]
  let tune = initial.tune
  let decay = initial.decay
  let tone = initial.tone

  const overlay = h('div', { class: 'overlay' })

  const tuneVal = h('span', {}, [`${tune.toFixed(1)} st`])
  const decayVal = h('span', {}, [`${decay.toFixed(2)}x`])
  const toneVal = h('span', {}, [tone.toFixed(2)])

  const push = () => engine.updateDrumVoiceParams(sound, { tune, decay, tone })

  const tuneSlider = h('input', {
    type: 'range',
    min: '-12',
    max: '12',
    step: '0.5',
    value: String(tune),
    oninput: (e: Event) => {
      tune = Number((e.target as HTMLInputElement).value)
      tuneVal.textContent = `${tune.toFixed(1)} st`
      push()
    }
  })

  const decaySlider = h('input', {
    type: 'range',
    min: '0.3',
    max: '2.5',
    step: '0.05',
    value: String(decay),
    oninput: (e: Event) => {
      decay = Number((e.target as HTMLInputElement).value)
      decayVal.textContent = `${decay.toFixed(2)}x`
      push()
    }
  })

  const toneSlider = h('input', {
    type: 'range',
    min: '0',
    max: '1',
    step: '0.01',
    value: String(tone),
    oninput: (e: Event) => {
      tone = Number((e.target as HTMLInputElement).value)
      toneVal.textContent = tone.toFixed(2)
      push()
    }
  })

  const close = () => overlay.remove()

  const dialog = h('div', { class: 'dialog' }, [
    h('h3', {}, [`Sound Design: ${DRUM_DISPLAY_NAMES[sound]}`]),
    h('p', { class: 'hint' }, ["Shape this pad's synthesis — every change previews instantly."]),
    h('label', {}, ['Tune']),
    h('div', { class: 'transport-row' }, [tuneSlider, tuneVal]),
    h('label', {}, ['Decay']),
    h('div', { class: 'transport-row' }, [decaySlider, decayVal]),
    h('label', {}, ['Tone']),
    h('div', { class: 'transport-row' }, [toneSlider, toneVal]),
    h('div', { class: 'dialog-actions' }, [
      h('button', { class: 'btn ghost', onclick: () => { tune = 0; decay = 1; tone = 0.5; tuneSlider.value = '0'; decaySlider.value = '1'; toneSlider.value = '0.5'; tuneVal.textContent = '0.0 st'; decayVal.textContent = '1.00x'; toneVal.textContent = '0.50'; push() } }, ['Reset']),
      h('button', { class: 'btn ghost', onclick: () => engine.triggerDrumPad(sound) }, ['Preview']),
      h('button', { class: 'btn primary', onclick: close }, ['Done'])
    ])
  ])

  overlay.append(dialog)
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) close()
  })
  document.body.append(overlay)
}
