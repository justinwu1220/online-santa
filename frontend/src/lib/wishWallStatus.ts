/**
 * 願望牆專用的狀態篩選與標籤。
 *
 * 刻意獨立於 `components/StatusBadge.tsx` 的 `WISH_TONES`：後台的「已完成」是正向的
 * 綠色，願望牆這裡的配色語意不同（未認領綠／已認領黃／已完成紅——強調「還能不能
 * 認領」而不是「流程是否順利結束」），硬共用會變成後台綠字、牆上紅字的矛盾。
 * 兩套系統各自獨立維護。
 */

export const WALL_STATUS_OPTIONS = [
  { value: 'AVAILABLE', label: '未認領' },
  { value: 'CLAIMED', label: '已認領' },
  { value: 'FULFILLED', label: '已完成' },
] as const

export type WallWishStatus = typeof WALL_STATUS_OPTIONS[number]['value']

const LABELS: Record<WallWishStatus, string> = {
  AVAILABLE: '未認領',
  CLAIMED: '已認領',
  FULFILLED: '已完成',
}

const TONES: Record<WallWishStatus, string> = {
  AVAILABLE: 'border-emerald-400/30 bg-emerald-400/15 text-emerald-200',
  CLAIMED: 'border-amber-400/30 bg-amber-400/15 text-amber-200',
  FULFILLED: 'border-red-400/30 bg-red-400/15 text-red-200',
}

/**
 * 願望卡片/詳情用的狀態標籤。
 *
 * @returns 標籤文字與對應配色 class；不在這三個狀態內（例如透過舊連結看到的
 *   ARCHIVED）就回 `null`，呼叫端不畫任何東西。
 */
export function wallStatusTag(status: string): { label: string; className: string } | null {
  if (!(status in LABELS)) return null
  const key = status as WallWishStatus
  return { label: LABELS[key], className: TONES[key] }
}
