import { useEffect, useRef, type RefObject } from 'react'
import { EASE_OUT, reducedMotion } from './motion'

/**
 * 對話多了一則訊息時，最後一則由下 8px 浮上（0.26s，設計稿 `.msg:last-child`）。
 * 第一次載入（原本就有的訊息）不播。
 */
export function useRiseLastOnGrow(container: RefObject<HTMLElement | null>, count: number | undefined) {
  const previous = useRef<number | undefined>(undefined)
  useEffect(() => {
    const before = previous.current
    previous.current = count
    if (before === undefined || count === undefined || count <= before || reducedMotion()) return
    const last = container.current?.querySelector('.msg:last-child')
    last?.animate([{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'none' }],
      { duration: 260, easing: EASE_OUT })
  }, [container, count])
}
