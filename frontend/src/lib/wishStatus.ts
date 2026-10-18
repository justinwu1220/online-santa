import type { ClaimStatus, WishStatus } from './types'

/**
 * 願望牆（公開頁）的三種狀態，與設計稿的 `data-status="open|claimed|done"` 對應。
 *
 * 所有顏色、icon、標籤樣式都由這個值推導（見 styles/rd.css），元件不各自判斷狀態。
 * 願望牆的配色語意是「還能不能認領」：可認領綠、已認領黃、已完成灰。
 */
export type PublicStatus = 'open' | 'claimed' | 'done'

export const PUBLIC_STATUS_LABELS: Record<PublicStatus, string> = {
  open: '可認領',
  claimed: '已認領',
  done: '已完成',
}

/** 願望牆「禮物狀態」篩選：值是後端的 WishStatus。 */
export const WALL_STATUS_OPTIONS = [
  { value: 'AVAILABLE', label: '可認領' },
  { value: 'CLAIMED', label: '已認領' },
  { value: 'FULFILLED', label: '已完成' },
] as const

/**
 * 公開頁上的狀態。ARCHIVED（機構下架）不會出現在願望牆，但舊連結仍然打得開，
 * 這時當成「已完成」的灰色樣式，標籤寫「已下架」。
 */
export function publicStatusOf(status: WishStatus): { key: PublicStatus; label: string } {
  switch (status) {
    case 'AVAILABLE':
      return { key: 'open', label: '可認領' }
    case 'CLAIMED':
      return { key: 'claimed', label: '已認領' }
    case 'FULFILLED':
      return { key: 'done', label: '已完成' }
    default:
      return { key: 'done', label: '已下架' }
  }
}

/**
 * 認領者自己的進度（我的認領）。標籤顯示的是「這筆認領走到哪」，不是願望的狀態。
 * 結束的認領（收回、取消）用灰色，不顯示步驟。
 */
export const MY_PROGRESS_LABELS = ['待寄送', '已寄出', '機構已收到', '流程完成'] as const

export function claimProgressOf(status: ClaimStatus): number | null {
  switch (status) {
    case 'CLAIMED': return 0
    case 'SHIPPED': return 1
    case 'RECEIVED': return 2
    case 'COMPLETED': return 3
    default: return null
  }
}

export function claimTabOf(status: ClaimStatus): { key: PublicStatus; label: string } {
  const progress = claimProgressOf(status)
  if (progress === null) {
    return { key: 'done', label: status === 'RELEASED' ? '已收回' : '已取消' }
  }
  return { key: progress === 3 ? 'done' : 'claimed', label: MY_PROGRESS_LABELS[progress] }
}
