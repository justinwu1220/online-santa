import { useEffect } from 'react'

/**
 * 彈窗或側邊抽屜開著時，鎖住底下頁面的捲動（滾輪、觸控、鍵盤都不會帶動後面的清單）。
 *
 * 多個彈窗疊在一起（抽屜上再開確認框）時用計數，全部關掉才解鎖。
 * 鎖定時網頁的捲軸會消失，內容會被撐寬一個捲軸的寬度，所以同時補上等寬的 padding-right，
 * 畫面不會跳動。
 */
let locks = 0
let previous: { overflow: string; paddingRight: string } | null = null

function lock() {
  if (locks++ > 0) return
  const root = document.documentElement
  const scrollbar = window.innerWidth - root.clientWidth
  previous = { overflow: root.style.overflow, paddingRight: root.style.paddingRight }
  root.style.overflow = 'hidden'
  if (scrollbar > 0) root.style.paddingRight = `${scrollbar}px`
}

function unlock() {
  if (locks === 0 || --locks > 0) return
  const root = document.documentElement
  root.style.overflow = previous?.overflow ?? ''
  root.style.paddingRight = previous?.paddingRight ?? ''
  previous = null
}

/** `open` 為 true 期間鎖住頁面捲動。 */
export function useScrollLock(open: boolean) {
  useEffect(() => {
    if (!open) return
    lock()
    return unlock
  }, [open])
}
