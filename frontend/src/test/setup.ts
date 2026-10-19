import { afterEach, vi } from 'vitest'

/**
 * 元件測試共用的環境補強。jsdom 沒有的瀏覽器功能（原生 `<dialog>`、Web Animations、
 * `scrollIntoView`、`matchMedia`）在這裡補上最小可用的版本；node 環境的純函式測試不受影響。
 */
if (typeof window !== 'undefined') {
  void import('@testing-library/jest-dom/vitest')

  // `<dialog>`：showModal／close 只改 open 屬性並觸發 close 事件（與瀏覽器的順序一致）
  const proto = window.HTMLDialogElement?.prototype
  if (proto) {
    proto.showModal = function showModal(this: HTMLDialogElement) { this.setAttribute('open', '') }
    proto.close = function close(this: HTMLDialogElement) {
      if (!this.hasAttribute('open')) return
      this.removeAttribute('open')
      this.dispatchEvent(new Event('close'))
    }
  }

  // Web Animations：animate() 回傳可取消的假動畫，並記在元素上讓測試檢查
  type FakeAnimation = { playState: string; cancel: () => void; effect: unknown; finished: Promise<void> }
  const registry = new WeakMap<Element, FakeAnimation[]>()
  window.Element.prototype.animate = function animate(this: Element, keyframes: unknown, options?: unknown) {
    const list = registry.get(this) ?? []
    const timing = typeof options === 'object' && options ? options as { fill?: string } : {}
    const animation: FakeAnimation = {
      playState: 'running',
      effect: { target: this, getTiming: () => timing, getKeyframes: () => keyframes },
      finished: Promise.resolve(),
      cancel() {
        animation.playState = 'idle'
        registry.set(this as unknown as Element, (registry.get(this as unknown as Element) ?? []).filter((a) => a !== animation))
      },
    }
    animation.cancel = () => {
      animation.playState = 'idle'
      registry.set(this, (registry.get(this) ?? []).filter((a) => a !== animation))
    }
    registry.set(this, [...list, animation])
    return animation as unknown as Animation
  }
  window.Element.prototype.getAnimations = function getAnimations(this: Element) {
    return (registry.get(this) ?? []) as unknown as Animation[]
  }
  window.Element.prototype.scrollIntoView = vi.fn()
  window.scrollTo = vi.fn() as unknown as typeof window.scrollTo

  window.matchMedia = window.matchMedia ?? ((query: string) => ({
    matches: false, media: query, onchange: null,
    addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn(),
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia
}

afterEach(async () => {
  if (typeof window === 'undefined') return
  const { cleanup } = await import('@testing-library/react')
  cleanup()
  document.documentElement.removeAttribute('style')
})
