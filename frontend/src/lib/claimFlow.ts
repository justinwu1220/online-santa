import type { ClaimEventType, ClaimStatus } from './types'

/**
 * 認領的主流程。每一步對應一個歷程事件，順序就是實際會走的順序。
 *
 * 認領者（我的認領）與機構（認領詳情）的進度條共用這一份，流程改了只要改這裡。
 */
const CLAIM_FLOW: { status: ClaimStatus; event: ClaimEventType }[] = [
  { status: 'CLAIMED', event: 'CLAIMED' },
  { status: 'SHIPPED', event: 'SHIPPED' },
  { status: 'RECEIVED', event: 'RECEIVED' },
  { status: 'COMPLETED', event: 'COMPLETED' },
]

/**
 * 還沒走到的步驟。認領已經結束（收回、逾期自動收回、取消）時流程不可能再往下走，
 * 就不列——狀態不在主流程裡的一律回傳空陣列。
 */
export function pendingStepsFor(status: ClaimStatus): ClaimEventType[] {
  const current = CLAIM_FLOW.findIndex((step) => step.status === status)
  if (current < 0) return []
  return CLAIM_FLOW.slice(current + 1).map((step) => step.event)
}
