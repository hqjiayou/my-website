/** 弹层栈：嵌套 FormDialog / 抽屉共享滚动锁、Esc 与焦点约束。 */

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

let scrollLockCount = 0
let previousBodyOverflow = ''

type ModalLayer = {
  panel: HTMLElement | null
  /** null = 危险确认等：吞掉 Esc，不关闭。 */
  onEscape: (() => void) | null
}

const layers: ModalLayer[] = []
let escapeBound = false

function onDocumentKeyDown(event: KeyboardEvent) {
  if (layers.length === 0) return
  const top = layers[layers.length - 1]
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    top.onEscape?.()
    return
  }
  if (event.key !== 'Tab' || !top.panel) return
  const focusable = getFocusableElements(top.panel)
  if (focusable.length === 0) {
    event.preventDefault()
    top.panel.focus()
    return
  }
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement
  if (event.shiftKey) {
    if (active === first || !top.panel.contains(active)) {
      event.preventDefault()
      last.focus()
    }
    return
  }
  if (active === last || !top.panel.contains(active)) {
    event.preventDefault()
    first.focus()
  }
}

function ensureEscapeListener() {
  if (escapeBound) return
  escapeBound = true
  document.addEventListener('keydown', onDocumentKeyDown, true)
}

function releaseEscapeListenerIfIdle() {
  if (layers.length > 0 || !escapeBound) return
  escapeBound = false
  document.removeEventListener('keydown', onDocumentKeyDown, true)
}

export function acquireBodyScrollLock(): () => void {
  if (typeof document === 'undefined') return () => undefined
  if (scrollLockCount === 0) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  }
  scrollLockCount += 1
  return () => {
    scrollLockCount = Math.max(0, scrollLockCount - 1)
    if (scrollLockCount === 0) {
      document.body.style.overflow = previousBodyOverflow
    }
  }
}

export function getFocusableElements(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
    (el) => !el.hasAttribute('disabled') && el.getAttribute('aria-hidden') !== 'true' && el.tabIndex !== -1,
  )
}

/**
 * 注册一层模态：滚动锁 + Esc/Tab 只由栈顶处理。
 * 关闭子确认时释放本层即可，不会把父抽屉的滚动锁一并解开。
 */
export function registerModalLayer(options: {
  panel: HTMLElement | null
  onEscape: (() => void) | null
  lockScroll?: boolean
}): () => void {
  const layer: ModalLayer = { panel: options.panel, onEscape: options.onEscape }
  layers.push(layer)
  ensureEscapeListener()
  const releaseScroll = options.lockScroll === false ? () => undefined : acquireBodyScrollLock()
  return () => {
    const index = layers.lastIndexOf(layer)
    if (index >= 0) layers.splice(index, 1)
    releaseScroll()
    releaseEscapeListenerIfIdle()
  }
}

export function focusInitialInPanel(panel: HTMLElement, preferred?: HTMLElement | null) {
  if (preferred) {
    preferred.focus()
    return
  }
  const focusable = getFocusableElements(panel)
  if (focusable[0]) {
    focusable[0].focus()
    return
  }
  panel.focus()
}
