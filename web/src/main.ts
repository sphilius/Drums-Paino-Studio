import './style.css'
import { engine } from './audio/engine'
import { restoreSession, startAutosave } from './persistence'
import { h, shareOrDownload, showToast } from './ui/dom'
import { mountPadsView } from './ui/padsView'
import { mountSequencerView } from './ui/sequencerView'
import { mountPianoRollView } from './ui/pianoRollView'
import { mountMixerView } from './ui/mixerView'
import { mountLibraryView } from './ui/libraryView'

restoreSession()
startAutosave()

interface TabDef {
  label: string
  icon: string
  mount: (el: HTMLElement) => () => void
}

const TABS: TabDef[] = [
  { label: 'Pads', icon: '\u{1F941}', mount: mountPadsView },
  { label: 'Sequencer', icon: '\u{1F39B}', mount: mountSequencerView },
  { label: 'Piano', icon: '\u{1F3B9}', mount: mountPianoRollView },
  { label: 'Mixer', icon: '\u{1F39A}', mount: mountMixerView },
  { label: 'Library', icon: '\u{1F4DA}', mount: mountLibraryView }
]

const app = document.getElementById('app')
if (!app) throw new Error('#app root element missing')

let activeTab = 0
let unmountCurrent: (() => void) | null = null

// ---------- Top bar ----------
const topbar = h('div', { class: 'topbar' }, [
  h('div', { class: 'brand' }, ['BEATCRAFT WEB']),
  h('button', {
    class: 'icon-btn',
    style: 'width:36px;height:36px;font-size:14px',
    onclick: () => {
      if (confirm('Clear the entire pattern?')) {
        engine.clearSequencer()
        showToast('Pattern cleared.')
        renderActiveTab()
      }
    }
  }, ['\u{1F5D1}'])
])

// ---------- Transport bar ----------
const stepLed = h('div', { class: 'step-led' }, [stepLedText()])
const playBtn = h('button', { class: 'icon-btn', onclick: () => engine.togglePlayback() }, ['\u{25B6}'])
const recordBtn = h('button', { class: 'icon-btn record', onclick: () => engine.toggleRecording() }, ['\u{23FA}'])

const bpmLabel = h('span', { class: 'tempo-label' }, [`TEMPO: ${engine.bpm} BPM`])
const bpmSlider = h('input', {
  type: 'range',
  min: '60',
  max: '200',
  step: '1',
  value: String(engine.bpm),
  oninput: (e: Event) => {
    const v = Number((e.target as HTMLInputElement).value)
    engine.setBpm(v)
    bpmLabel.textContent = `TEMPO: ${v} BPM`
  }
})

let exportMenuOpen = false
const exportDropdown = h('div', { class: 'dropdown' }, [])
exportDropdown.style.display = 'none'

function buildExportMenu() {
  exportDropdown.innerHTML = ''
  exportDropdown.append(
    h('button', {
      class: 'dropdown-item',
      onclick: async () => {
        closeExportMenu()
        showToast('Rendering full mix...')
        const blob = await engine.exportWavBlob()
        await shareOrDownload(blob, `BeatCraft_Mix_${Date.now()}.wav`, 'BeatCraft mix')
      }
    }, ['\u{1F3B5} Full Mix (WAV)']),
    h('button', {
      class: 'dropdown-item',
      onclick: async () => {
        closeExportMenu()
        showToast('Rendering stems...')
        const stems = await engine.exportStemBlobs()
        for (const stem of stems) {
          await shareOrDownload(stem.blob, `BeatCraft_${stem.label}_${Date.now()}.wav`, `BeatCraft ${stem.label} stem`)
        }
      }
    }, ['\u{1F4DA} Stems (Drums/Synth)']),
    h('button', {
      class: 'dropdown-item',
      onclick: async () => {
        closeExportMenu()
        const blob = engine.exportMidiBlob()
        await shareOrDownload(blob, `BeatCraft_${Date.now()}.mid`, 'BeatCraft MIDI file')
      }
    }, ['\u{1F3B9} MIDI File (.mid)'])
  )
}

function closeExportMenu() {
  exportMenuOpen = false
  exportDropdown.style.display = 'none'
}

function toggleExportMenu() {
  exportMenuOpen = !exportMenuOpen
  exportDropdown.style.display = exportMenuOpen ? 'block' : 'none'
}

document.addEventListener('click', (e) => {
  if (exportMenuOpen && !exportDropdown.contains(e.target as Node) && !(e.target as HTMLElement).closest('.export-btn')) {
    closeExportMenu()
  }
})

buildExportMenu()

const exportBtn = h('button', {
  class: 'pill-btn export-btn',
  onclick: (e: Event) => {
    e.stopPropagation()
    toggleExportMenu()
  }
}, ['\u{2B07} EXPORT'])

const transport = h('div', { class: 'transport' }, [
  h('div', { class: 'transport-row controls' }, [
    h('div', { style: 'display:flex;gap:12px' }, [playBtn, recordBtn]),
    stepLed,
    h('div', { class: 'dropdown-wrap' }, [exportBtn, exportDropdown])
  ]),
  h('div', { class: 'tempo-row' }, [bpmLabel, bpmSlider])
])

function stepLedText(): string {
  const step = engine.currentStep
  return `STEP ${String(step + 1).padStart(2, '0')}/${String(engine.totalSteps).padStart(2, '0')}`
}

// ---------- Tab bar ----------
const tabButtons: HTMLButtonElement[] = []
const tabbar = h(
  'div',
  { class: 'tabbar' },
  TABS.map((tab, index) => {
    const btn = h(
      'button',
      {
        class: `tab-btn${index === activeTab ? ' active' : ''}`,
        onclick: () => switchTab(index)
      },
      [h('div', { class: 'tab-icon' }, [tab.icon]), h('div', {}, [tab.label])]
    )
    tabButtons.push(btn)
    return btn
  })
)

// ---------- Content ----------
const content = h('div', { class: 'content' })

const mainCol = h('div', { class: 'main-col' }, [topbar, transport, content])
app.append(mainCol, tabbar)

function switchTab(index: number) {
  if (index === activeTab && unmountCurrent) return
  activeTab = index
  tabButtons.forEach((btn, i) => btn.classList.toggle('active', i === index))
  renderActiveTab()
}

function renderActiveTab() {
  if (unmountCurrent) unmountCurrent()
  unmountCurrent = TABS[activeTab].mount(content)
}

renderActiveTab()

// ---------- Live engine feedback ----------
engine.onPlaybackChange((playing) => {
  playBtn.textContent = playing ? '\u{23F8}' : '\u{25B6}'
  playBtn.classList.toggle('active', playing)
  recordBtn.classList.toggle('active', engine.isRecordingSeq)
})

engine.onStepChange(() => {
  stepLed.textContent = stepLedText()
})
