import { useLayoutEffect, useRef, type ReactNode } from 'react'
import { reducedMotion } from '../../lib/motion'

/**
 * 登入／註冊卡片內切換畫面（SPEC 12.4）：新畫面從 `order` 較大的一側滑入（往下一步向左、
 * 回上一步向右），卡片高度同步平順伸縮。只在 `screen` 改變時播放；內容預設可見，動畫沒播也不會卡住。
 */
export function SlideSwap({ screen, order, className, children }: {
  screen: string
  order: number
  className?: string
  children: ReactNode
}) {
  const box = useRef<HTMLDivElement>(null)
  const last = useRef({ screen, order, height: 0 })

  useLayoutEffect(() => {
    const el = box.current
    if (!el) return
    const prev = last.current
    const height = el.offsetHeight
    last.current = { screen, order, height }
    if (prev.screen === screen || reducedMotion()) return

    const dir = order >= prev.order ? 1 : -1
    el.animate(
      [{ opacity: 0, transform: `translateX(${28 * dir}px)` }, { opacity: 1, transform: 'none' }],
      { duration: 340, easing: 'cubic-bezier(.22,.8,.3,1)', fill: 'backwards' })
    if (prev.height && prev.height !== height) {
      el.animate(
        [{ height: `${prev.height}px`, overflow: 'hidden' }, { height: `${height}px`, overflow: 'hidden' }],
        { duration: 340, easing: 'cubic-bezier(.22,.8,.3,1)' })
    }
  }, [screen, order])

  return <div ref={box} className={className}>{children}</div>
}
