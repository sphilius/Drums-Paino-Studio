import { engine } from '../audio/engine'
import { BUILT_IN_SOUND_PACKS, type SoundPack } from '../audio/soundPacks'
import {
  decodeSoundPack,
  decodeSynthPatch,
  deletePreset,
  getPresetsByType,
  importSoundPackJson,
  importSynthPatchJson,
  saveSoundPack,
  saveSynthPatch,
  subscribe as subscribeLibrary,
  type PresetEntity
} from '../library'
import { h, clear, showToast, shareOrCopyText } from './dom'

function openSaveDialog(title: string, onConfirm: (name: string, author: string) => void) {
  const overlay = h('div', { class: 'overlay' })
  const nameInput = h('input', { type: 'text', placeholder: 'Name' })
  const authorInput = h('input', { type: 'text', placeholder: 'Your name (optional)' })
  const close = () => overlay.remove()
  overlay.append(
    h('div', { class: 'dialog' }, [
      h('h3', {}, [title]),
      h('label', {}, ['Name']),
      nameInput,
      h('label', {}, ['Author']),
      authorInput,
      h('div', { class: 'dialog-actions' }, [
        h('button', { class: 'btn ghost', onclick: close }, ['Cancel']),
        h('button', {
          class: 'btn primary',
          onclick: () => {
            if (!nameInput.value.trim()) return
            onConfirm(nameInput.value.trim(), authorInput.value.trim())
            close()
          }
        }, ['Save'])
      ])
    ])
  )
  overlay.addEventListener('click', (e) => e.target === overlay && close())
  document.body.append(overlay)
}

function openImportDialog(title: string, onConfirm: (json: string) => void) {
  const overlay = h('div', { class: 'overlay' })
  const jsonInput = h('textarea', { rows: '6', placeholder: 'Paste preset JSON here' })
  const close = () => overlay.remove()
  overlay.append(
    h('div', { class: 'dialog' }, [
      h('h3', {}, [title]),
      h('p', { class: 'hint' }, ['Paste JSON shared by another producer.']),
      jsonInput,
      h('div', { class: 'dialog-actions' }, [
        h('button', { class: 'btn ghost', onclick: close }, ['Cancel']),
        h('button', {
          class: 'btn primary',
          onclick: () => {
            if (!jsonInput.value.trim()) return
            onConfirm(jsonInput.value.trim())
            close()
          }
        }, ['Import'])
      ])
    ])
  )
  overlay.addEventListener('click', (e) => e.target === overlay && close())
  document.body.append(overlay)
}

export function mountLibraryView(container: HTMLElement): () => void {
  let section: 'packs' | 'synth' = 'packs'

  const root = h('div', {})
  clear(container)
  container.append(root)

  function render() {
    clear(root)
    root.append(
      h('div', { class: 'section-title' }, ['COMMUNITY LIBRARY']),
      h('p', { class: 'hint', style: 'text-align:left;margin:0 0 12px' }, [
        'Save, apply, and share sound packs & synth presets as JSON — no account needed.'
      ]),
      h('div', { class: 'lib-tabs' }, [
        h('button', {
          class: `lib-tab-btn${section === 'packs' ? ' active' : ''}`,
          onclick: () => {
            section = 'packs'
            render()
          }
        }, ['Sound Packs']),
        h('button', {
          class: `lib-tab-btn${section === 'synth' ? ' active' : ''}`,
          onclick: () => {
            section = 'synth'
            render()
          }
        }, ['Synth Presets'])
      ]),
      section === 'packs' ? renderPacksSection() : renderSynthSection()
    )
  }

  function renderPacksSection(): HTMLElement {
    const userPacks = getPresetsByType('SOUND_PACK')
    return h('div', {}, [
      h('div', { class: 'row', style: 'display:flex;justify-content:space-between;align-items:center;margin-bottom:8px' }, [
        h('div', { style: 'font-size:11px;font-weight:700;color:var(--pink)' }, ['BUILT-IN PACKS']),
        h('div', {}, [
          h('button', {
            class: 'small-btn',
            onclick: () =>
              openImportDialog('Import Sound Pack', (json) => {
                const entity = importSoundPackJson(json)
                showToast(entity ? `Imported sound pack '${entity.name}'` : 'Could not parse that sound pack JSON.')
              })
          }, ['Import']),
          h('button', {
            class: 'small-btn',
            onclick: () =>
              openSaveDialog('Save Sound Pack', (name, author) => {
                const params = { ...engine.drumVoiceParams }
                const pack: SoundPack = {
                  id: `user_${Date.now()}`,
                  name,
                  author: author || 'Anonymous',
                  description: 'Custom pad tuning shared from BeatCraft Web.',
                  voiceParams: params
                }
                saveSoundPack(pack, pack.author)
                showToast(`Saved sound pack '${name}' to your library!`)
              })
          }, ['Save Current'])
        ])
      ]),
      ...BUILT_IN_SOUND_PACKS.map((pack) =>
        h(
          'div',
          {
            class: `pack-card${engine.activeSoundPackName === pack.name ? ' active' : ''}`,
            onclick: () => {
              engine.applySoundPack(pack)
              showToast(`Applied sound pack: ${pack.name}`)
              render()
            }
          },
          [
            h('div', { class: 'row' }, [
              h('strong', {}, [pack.name]),
              engine.activeSoundPackName === pack.name ? h('span', { style: 'color:var(--green);font-size:9px;font-weight:700' }, ['ACTIVE']) : null
            ]),
            h('div', { style: 'color:var(--text-dim);font-size:9px' }, [`by ${pack.author}`]),
            h('div', { style: 'font-size:10px;margin-top:4px' }, [pack.description])
          ]
        )
      ),
      h('div', { style: 'font-size:11px;font-weight:700;color:var(--green);margin:14px 0 8px' }, ['MY PACKS']),
      userPacks.length === 0
        ? h('p', { class: 'hint', style: 'text-align:left' }, ['No custom packs saved yet. Tune a pad (tap the \u{1F39B} icon) and save it here!'])
        : h('div', {}, userPacks.map((entity) => renderPresetCard(entity)))
    ])
  }

  function renderSynthSection(): HTMLElement {
    const userPresets = getPresetsByType('SYNTH_PATCH')
    return h('div', {}, [
      h('div', { class: 'row', style: 'display:flex;justify-content:space-between;align-items:center;margin-bottom:8px' }, [
        h('div', { style: 'font-size:11px;font-weight:700;color:var(--pink)' }, ['MY SYNTH PRESETS']),
        h('div', {}, [
          h('button', {
            class: 'small-btn',
            onclick: () =>
              openImportDialog('Import Synth Preset', (json) => {
                const entity = importSynthPatchJson(json)
                showToast(entity ? 'Imported synth preset.' : 'Could not parse that synth preset JSON.')
              })
          }, ['Import']),
          h('button', {
            class: 'small-btn',
            onclick: () =>
              openSaveDialog('Save Synth Preset', (name, author) => {
                saveSynthPatch(name, author || 'Anonymous', engine.currentSynthPatch())
                showToast(`Saved synth preset '${name}' to your library!`)
              })
          }, ['Save Current'])
        ])
      ]),
      userPresets.length === 0
        ? h('p', { class: 'hint', style: 'text-align:left' }, ['No synth presets saved yet. Dial in a waveform + envelope on the Piano tab and save it here!'])
        : h('div', {}, userPresets.map((entity) => renderPresetCard(entity)))
    ])
  }

  function renderPresetCard(entity: PresetEntity): HTMLElement {
    return h('div', { class: 'preset-card' }, [
      h('div', { class: 'row' }, [
        h('div', { style: 'flex:1;cursor:pointer', onclick: () => applyPreset(entity) }, [
          h('strong', {}, [entity.name]),
          h('div', { style: 'color:var(--text-dim);font-size:9px' }, [`by ${entity.author}`])
        ]),
        h('button', { class: 'small-btn', onclick: () => shareOrCopyText(entity.payloadJson, entity.name) }, ['Share']),
        h('button', {
          class: 'small-btn danger',
          onclick: () => {
            deletePreset(entity.id)
            showToast(`Removed '${entity.name}' from your library.`)
          }
        }, ['Delete'])
      ])
    ])
  }

  function applyPreset(entity: PresetEntity) {
    if (entity.type === 'SOUND_PACK') {
      const pack = decodeSoundPack(entity)
      if (pack) {
        engine.applySoundPack(pack)
        showToast(`Applied sound pack: ${pack.name}`)
      }
    } else {
      const patch = decodeSynthPatch(entity)
      if (patch) {
        engine.applySynthPatch(patch)
        showToast(`Applied synth preset '${entity.name}'`)
      }
    }
  }

  render()
  const unsubLibrary = subscribeLibrary(render)
  const unsubPack = engine.onSoundPackChange(render)

  return () => {
    unsubLibrary()
    unsubPack()
  }
}
