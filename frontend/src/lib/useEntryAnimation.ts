import { useEffect, useRef } from 'react'
import {
  envelopeRiseDelay, listReturn, markSpecialNavigation, reducedMotion, rise, savedScroll,
} from './motion'

/**
 * 列表頁（願望牆、我的認領）的進場（SPEC 6.2）。
 *
 * 從詳細頁回來時：還原離開前的捲動位置，並淡入 0.3s（不播信封）。資料要等到畫面撐得夠高
 * （`ready`）才還原，否則捲動位置會被截短。其他情況回到頂端的工作由 TransitionProvider 做。
 */
export function useListEntry(path: string, ready: boolean) {
  const done = useRef(false)

  useEffect(() => {
    if (done.current || !ready || !listReturn.pending) return
    done.current = true
    listReturn.pending = false
    markSpecialNavigation()
    window.scrollTo(0, savedScroll(path))
    const page = document.querySelector('.rd .page')
    if (page && !reducedMotion()) {
      page.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 300, easing: 'ease-out' })
    }
  }, [path, ready])
}

/**
 * 詳細頁的進場：大卡片由下 24px 浮上（0.7s），其後區塊各晚 0.12s。
 *
 * 從拆信轉場過來時，延後到遮罩開始淡出的那一刻（`envelopeRiseDelay`）；直接開網址（分享連結、
 * 重新整理）不播信封，只讓大卡片浮上。`ready` 為內容已經渲染出來。
 */
export function useDetailEntry(root: React.RefObject<HTMLElement | null>, ready: boolean, enabled = true) {
  const done = useRef(false)

  useEffect(() => {
    if (done.current || !ready || !enabled || !root.current) return
    done.current = true
    rise(
      [root.current.querySelector('.wish'), root.current.querySelector('.org, .task')],
      { delay: envelopeRiseDelay(), stagger: 120, dist: 24, dur: 700 },
    )
  }, [root, ready, enabled])
}

/** 沒有共用轉場的頁面（LoginPage、OrgApply）進場：由下 16px 浮上 0.45s。`ready` 為頁面內容已經渲染出來。 */
export function usePageEnter(ready = true) {
  const done = useRef(false)
  useEffect(() => {
    if (done.current || !ready) return
    done.current = true
    rise([document.querySelector('.rd .page')], { dist: 16, dur: 450 })
  }, [ready])
}
