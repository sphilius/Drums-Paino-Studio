type Attrs = Record<string, string | number | boolean | ((ev: Event) => void) | undefined>

/** Minimal hyperscript-style element builder to keep vanilla-DOM views readable. */
export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Attrs = {},
  children: (Node | string | null | undefined)[] = []
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag)
  for (const [key, value] of Object.entries(attrs)) {
    if (value === undefined) continue
    if (key.startsWith('on') && typeof value === 'function') {
      el.addEventListener(key.slice(2).toLowerCase(), value as EventListener)
    } else if (key === 'class') {
      el.className = String(value)
    } else if (typeof value === 'boolean') {
      if (value) el.setAttribute(key, '')
    } else {
      el.setAttribute(key, String(value))
    }
  }
  for (const child of children) {
    if (child === null || child === undefined) continue
    el.append(typeof child === 'string' ? document.createTextNode(child) : child)
  }
  return el
}

export function clear(el: HTMLElement) {
  el.innerHTML = ''
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 4000)
}

let toastTimer: number | undefined

export function showToast(message: string) {
  let toast = document.getElementById('toast')
  if (!toast) {
    toast = document.createElement('div')
    toast.id = 'toast'
    toast.style.cssText =
      'position:fixed;left:50%;bottom:24px;transform:translateX(-50%);background:#1a1d27;color:#fff;' +
      'border:1px solid rgba(255,255,255,0.15);border-radius:8px;padding:10px 16px;font-size:12px;' +
      'z-index:50;max-width:90vw;text-align:center;box-shadow:0 4px 16px rgba(0,0,0,0.4);transition:opacity 200ms'
    document.body.append(toast)
  }
  toast.textContent = message
  toast.style.opacity = '1'
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    if (toast) toast.style.opacity = '0'
  }, 2600)
}

export async function shareOrDownload(blob: Blob, filename: string, title: string) {
  const file = new File([blob], filename, { type: blob.type })
  const nav = navigator as Navigator & { canShare?: (data: { files: File[] }) => boolean; share?: (data: unknown) => Promise<void> }
  if (nav.canShare && nav.canShare({ files: [file] }) && nav.share) {
    try {
      await nav.share({ files: [file], title })
      return
    } catch {
      // fall through to plain download if the user cancels or share fails
    }
  }
  downloadBlob(blob, filename)
  showToast(`Downloaded ${filename}`)
}

export async function shareOrCopyText(text: string, title: string) {
  const nav = navigator as Navigator & { share?: (data: unknown) => Promise<void> }
  if (nav.share) {
    try {
      await nav.share({ title, text })
      return
    } catch {
      // fall through to clipboard copy if the user cancels or share fails
    }
  }
  try {
    await navigator.clipboard.writeText(text)
    showToast('Copied preset JSON to clipboard')
  } catch {
    showToast('Could not copy — select and copy the JSON manually')
  }
}
