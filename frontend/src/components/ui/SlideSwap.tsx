import { useCallback, useRef } from 'react'
import { flushSync } from 'react-dom'
import { EASE_OUT, reducedMotion, wait } from '../../lib/motion'

/**
 * 登入／註冊卡片內切換畫面（SPEC 12.4，依 prototype.html 的 swapScreen）：
 * 舊內容往外滑 28px 並淡出（0.16s）→ 等 160ms 才換內容 → 卡片高度由舊值動到新值（0.34s）、
 * 新內容從另一側滑入（0.34s）。往下一步 `dir` 為 1（向左滑），回上一步為 -1（向右滑）。
 *
 * `early` 在動畫一開始就執行（膠囊滑塊先動，跟著手勢）。`ref` 掛在要滑動的內容容器上，
 * 卡片是它最近的 `.auth-card`。內容預設可見：動畫被取代或沒播完，都不會留下透明的內容。
 */
export function useSlideSwap() {
  const body = useRef<HTMLDivElement>(null)
  const serial = useRef(0)
  const lastOut = useRef<Animation | null>(null)

  const swap = useCallback(async (dir: 1 | -1, apply: () => void, early?: () => void) => {
    const el = body.current
    early?.()
    if (!el || reducedMotion()) {
      apply()
      return
    }
    const id = ++serial.current
    const card = el.closest<HTMLElement>('.auth-card') ?? el
    const h0 = card.offsetHeight

    lastOut.current?.cancel()
    const out = el.animate(
      [{ opacity: 1, transform: 'none' }, { opacity: 0, transform: `translateX(${-28 * dir}px)` }],
      { duration: 160, easing: 'ease-in', fill: 'forwards' })
    lastOut.current = out
    await wait(160)
    // 更新的切換接手了：它自己會換內容，這一次不再做
    if (id !== serial.current) return

    flushSync(apply)
    out.cancel()
    lastOut.current = null
    const h1 = card.offsetHeight
    card.animate([{ height: `${h0}px` }, { height: `${h1}px` }], { duration: 340, easing: EASE_OUT })
    el.animate(
      [{ opacity: 0, transform: `translateX(${28 * dir}px)` }, { opacity: 1, transform: 'none' }],
      { duration: 340, easing: EASE_OUT, fill: 'backwards' })
    if (card.getBoundingClientRect().top < 80) window.scrollTo({ top: 0, behavior: 'smooth' })
  }, [])

  return { body, swap }
}
