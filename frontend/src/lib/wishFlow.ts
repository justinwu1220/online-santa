import type { WishStage, WishStatus } from './types'

/**
 * 機構後台把「願望＋目前認領」合成的單一狀態（後端算好的 `stage`）如何顯示、可以做什麼。
 * 元件只吃這裡的結果，不各自再判斷一次。
 */
export type ChipKey = 'draft' | 'review' | 'open' | 'claimed' | 'done'

/** 抽屜強調色（設計稿 `data-tone`）：狀態色同時套用到分頁底線、進度、引言線、主要按鈕。 */
export type Tone = 'draft' | 'review' | 'open' | 'claimed' | 'late' | 'done'

export interface StageInfo {
  /** 清單與抽屜標題旁的狀態標籤（設計稿五種） */
  chip: ChipKey
  label: string
  tone: Tone
}

export const CHIP_LABEL: Record<ChipKey, string> = {
  draft: '草稿', review: '審核中', open: '可認領', claimed: '已認領', done: '已完成',
}

export const STAGE_INFO: Record<WishStage, StageInfo> = {
  DRAFT: { chip: 'draft', label: CHIP_LABEL.draft, tone: 'draft' },
  PENDING_REVIEW: { chip: 'review', label: CHIP_LABEL.review, tone: 'review' },
  OPEN: { chip: 'open', label: CHIP_LABEL.open, tone: 'open' },
  CLAIMED_WAITING: { chip: 'claimed', label: CHIP_LABEL.claimed, tone: 'claimed' },
  CLAIMED_OVERDUE: { chip: 'claimed', label: CHIP_LABEL.claimed, tone: 'late' },
  SHIPPED: { chip: 'claimed', label: CHIP_LABEL.claimed, tone: 'claimed' },
  DONE: { chip: 'done', label: CHIP_LABEL.done, tone: 'done' },
  // 資料異常：不給任何動作，只提示；顏色用灰
  INCONSISTENT: { chip: 'claimed', label: CHIP_LABEL.claimed, tone: 'done' },
}

/** 篩選列（設計稿 ORG_FILTERS）：key 對應後端 `status` 篩選。 */
export type FilterKey = 'all' | ChipKey

export const FILTERS: { key: FilterKey; label: string; status?: WishStatus }[] = [
  { key: 'all', label: '全部' },
  { key: 'draft', label: CHIP_LABEL.draft, status: 'DRAFT' },
  { key: 'review', label: CHIP_LABEL.review, status: 'PENDING_REVIEW' },
  { key: 'open', label: CHIP_LABEL.open, status: 'AVAILABLE' },
  { key: 'claimed', label: CHIP_LABEL.claimed, status: 'CLAIMED' },
  { key: 'done', label: CHIP_LABEL.done, status: 'FULFILLED' },
]

/** 清單排序：草稿 → 審核中 → 可認領 → 已認領 → 已完成（設計稿 ORG_ORDER）。 */
const ORDER: Record<ChipKey, number> = { draft: 0, review: 1, open: 2, claimed: 3, done: 4 }

export function compareByStage(a: WishStage, b: WishStage): number {
  return ORDER[STAGE_INFO[a].chip] - ORDER[STAGE_INFO[b].chip]
}

/** 這個階段機構可以做的事（抽屜底部的按鈕）。 */
export interface StageActions {
  edit: boolean
  remove: boolean
  submit: boolean
  withdraw: boolean
  recall: boolean
  /** 確認收到並完成 */
  confirm: boolean
  /** 回報異常（帶到與團隊對話並預填） */
  report: boolean
}

export function actionsOf(stage: WishStage): StageActions {
  return {
    edit: stage === 'DRAFT',
    remove: stage === 'DRAFT',
    submit: stage === 'DRAFT',
    withdraw: stage === 'PENDING_REVIEW',
    recall: stage === 'CLAIMED_WAITING' || stage === 'CLAIMED_OVERDUE',
    confirm: stage === 'SHIPPED',
    report: stage === 'SHIPPED',
  }
}
