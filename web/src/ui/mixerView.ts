import { engine } from '../audio/engine'
import { h, clear } from './dom'

function faderColumn(label: string, color: string, value: number, onChange: (v: number) => void): HTMLElement {
  const pct = h('span', { class: 'pct' }, [`${Math.round(value * 100)}%`])
  const slider = h('input', {
    type: 'range',
    min: '0',
    max: '1.2',
    step: '0.01',
    value: String(value),
    style: `accent-color:${color}`,
    oninput: (e: Event) => {
      const v = Number((e.target as HTMLInputElement).value)
      pct.textContent = `${Math.round(v * 100)}%`
      onChange(v)
    }
  })
  return h('div', { class: 'fader-col', style: `border-color:${color}` }, [
    h('div', { class: 'label', style: `color:${color}` }, [label]),
    slider,
    pct
  ])
}

export function mountMixerView(container: HTMLElement): () => void {
  clear(container)
  container.append(
    h('div', {}, [
      h('div', { class: 'section-title' }, ['STUDIO MIXING CONSOLE']),
      h('div', { class: 'mixer-row' }, [
        faderColumn('DRUMS', 'var(--pink)', engine.drumVolume, (v) => engine.setDrumVolume(v)),
        faderColumn('SYNTH', 'var(--blue)', engine.synthVolume, (v) => engine.setSynthVolume(v)),
        faderColumn('MASTER', 'var(--orange)', engine.masterVolume, (v) => engine.setMasterVolume(v))
      ]),
      h('p', { class: 'hint' }, ['Faders allow up to 120% gain overhead — watch for clipping on the master bus.'])
    ])
  )

  return () => {
    /* no engine subscriptions held by this view */
  }
}
