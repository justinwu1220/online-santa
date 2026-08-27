import type { ClaimEventType, ClaimStatus, OrganizationStatus, WishStatus } from './types'

/** 一致的日期顯示。後端一律回 UTC ISO 字串，這裡轉成台灣時間。 */
export function formatDate(iso?: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('zh-TW', {
    year: 'numeric', month: 'long', day: 'numeric',
  })
}

export function formatDateTime(iso?: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('zh-TW', {
    year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

/** 距離期限還有幾天，過期回負數。 */
export function daysUntil(iso?: string) {
  if (!iso) return null
  const millis = new Date(iso).getTime() - Date.now()
  return Math.ceil(millis / (1000 * 60 * 60 * 24))
}

export const WISH_STATUS_LABELS: Record<WishStatus, string> = {
  DRAFT: '草稿',
  AVAILABLE: '可認領',
  CLAIMED: '已被認領',
  FULFILLED: '已完成',
  ARCHIVED: '已下架',
}

export const CLAIM_STATUS_LABELS: Record<ClaimStatus, string> = {
  CLAIMED: '待寄送',
  SHIPPED: '運送中',
  RECEIVED: '機構已收到',
  COMPLETED: '已完成',
  RELEASED: '已收回',
  CANCELLED: '已取消',
}

export const ORGANIZATION_STATUS_LABELS: Record<OrganizationStatus, string> = {
  PENDING: '待審核',
  APPROVED: '已核准',
  REJECTED: '已退件',
  SUSPENDED: '已停權',
}

export const CLAIM_EVENT_LABELS: Record<ClaimEventType, string> = {
  CLAIMED: '認領了這個願望',
  SHIPPED: '寄出禮物',
  RECEIVED: '機構確認收到',
  COMPLETED: '流程完成',
  RELEASED_MANUAL: '機構收回認領',
  RELEASED_AUTO: '逾期未寄送，系統自動收回',
  CANCELLED: '取消認領',
  FEEDBACK_UPLOADED: '機構上傳了回饋照片',
}
