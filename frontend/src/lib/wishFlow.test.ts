import { describe, expect, it } from 'vitest'
import type { WishStage } from './types'
import { actionsOf, chipLabelOf, compareByStage, STAGE_INFO } from './wishFlow'

const STAGES = Object.keys(STAGE_INFO) as WishStage[]

describe('actionsOf（每個階段機構可以做的事）', () => {
  it('草稿：編輯、刪除、提交審核', () => {
    expect(actionsOf('DRAFT')).toMatchObject({ edit: true, remove: true, submit: true, withdraw: false })
  })

  it('審核中：只能撤回', () => {
    const a = actionsOf('PENDING_REVIEW')
    expect(a.withdraw).toBe(true)
    expect([a.edit, a.remove, a.submit, a.recall, a.confirm]).toEqual([false, false, false, false, false])
  })

  it('可認領：沒有任何動作（不能編輯、不能自行下架）', () => {
    expect(Object.values(actionsOf('OPEN')).every((v) => v === false)).toBe(true)
  })

  it('已認領、還沒寄出（含逾期）：可以收回', () => {
    expect(actionsOf('CLAIMED_WAITING').recall).toBe(true)
    expect(actionsOf('CLAIMED_OVERDUE').recall).toBe(true)
  })

  it('已寄出：回報異常與確認收到並完成；不能收回', () => {
    const a = actionsOf('SHIPPED')
    expect(a.confirm).toBe(true)
    expect(a.report).toBe(true)
    expect(a.recall).toBe(false)
  })

  it('上架中且還沒人認領：可以「修改並重新送審」；已被認領就沒有這個動作', () => {
    expect(actionsOf('OPEN', { deletable: false, canBeginEdit: true, canRestore: false }).beginEdit).toBe(true)
    for (const stage of ['CLAIMED_WAITING', 'CLAIMED_OVERDUE', 'SHIPPED', 'DONE'] as const) {
      expect(actionsOf(stage, { deletable: false, canBeginEdit: false, canRestore: false }).beginEdit).toBe(false)
      expect(actionsOf(stage).edit).toBe(false)   // 認領後內容鎖定
    }
  })

  it('曾經上架過的草稿（下架修改中、被退回）不能刪除；內容沒改才能恢復上架', () => {
    const takenDown = { deletable: false, canBeginEdit: false, canRestore: true }
    expect(actionsOf('DRAFT', takenDown)).toMatchObject({ edit: true, remove: false, submit: true, restore: true })
    expect(actionsOf('DRAFT', { ...takenDown, canRestore: false }).restore).toBe(false)
    expect(actionsOf('DRAFT', { deletable: true, canBeginEdit: false, canRestore: false }).remove).toBe(true)
  })

  it('已完成與資料異常：沒有動作', () => {
    expect(Object.values(actionsOf('DONE')).every((v) => v === false)).toBe(true)
    expect(Object.values(actionsOf('INCONSISTENT')).every((v) => v === false)).toBe(true)
  })
})

describe('STAGE_INFO', () => {
  it('每個階段都有標籤與強調色；逾期是紅色（late），已完成是灰色', () => {
    for (const stage of STAGES) {
      expect(STAGE_INFO[stage].label).toBeTruthy()
      expect(STAGE_INFO[stage].tone).toBeTruthy()
    }
    expect(STAGE_INFO.CLAIMED_OVERDUE.tone).toBe('late')
    expect(STAGE_INFO.DONE.tone).toBe('done')
  })

  it('標籤只有設計稿的五種，已認領的三個細項共用同一個標籤', () => {
    const labels = new Set(STAGES.filter((s) => s !== 'INCONSISTENT').map((s) => STAGE_INFO[s].label))
    expect([...labels].sort()).toEqual(['可認領', '已完成', '已認領', '審核中', '草稿'].sort())
  })
})

describe('compareByStage', () => {
  it('草稿 → 審核中 → 可認領 → 已認領 → 已完成', () => {
    const sorted = ['DONE', 'SHIPPED', 'OPEN', 'PENDING_REVIEW', 'DRAFT', 'CLAIMED_OVERDUE'] as WishStage[]
    sorted.sort(compareByStage)
    expect(sorted).toEqual(['DRAFT', 'PENDING_REVIEW', 'OPEN', 'SHIPPED', 'CLAIMED_OVERDUE', 'DONE'])
  })
})

describe('chipLabelOf（狀態標籤文字）', () => {
  it('曾經上架過的草稿標示為「下架修改中」，其他維持原本的名稱', () => {
    expect(chipLabelOf({ stage: 'DRAFT', takenDown: true })).toBe('下架修改中')
    expect(chipLabelOf({ stage: 'DRAFT', takenDown: false })).toBe('草稿')
    expect(chipLabelOf({ stage: 'PENDING_REVIEW', takenDown: false })).toBe('審核中')
  })
})
