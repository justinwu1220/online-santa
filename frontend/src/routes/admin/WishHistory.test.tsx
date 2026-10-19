// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { WishChanges, WishHistoryItem } from '../../lib/types'
import { ResubmitChanges, WishHistoryTimeline } from './WishHistory'

const changes = (patch: Partial<WishChanges> = {}): WishChanges => ({
  lastApprovedAt: '2026-10-01T02:00:00Z',
  fields: [{ field: '願望標題', before: '色鉛筆', after: '水彩組' }],
  lettersBefore: 1, lettersAfter: 1, lettersChanged: false,
  ...patch,
})

describe('ResubmitChanges（重新送審的變更內容）', () => {
  it('列出每個文字欄位的「舊 → 新」，沒有更換信件就不提信件', () => {
    render(<ResubmitChanges changes={changes()} />)
    expect(screen.getByText('願望標題')).toBeInTheDocument()
    expect(screen.getByText('色鉛筆')).toBeInTheDocument()
    expect(screen.getByText('水彩組')).toBeInTheDocument()
    expect(screen.queryByText('孩子的手寫信件照片')).not.toBeInTheDocument()
  })

  it('信件照片被更換：顯示張數變化並提醒看上方的新照片', () => {
    render(<ResubmitChanges changes={changes({ fields: [], lettersChanged: true, lettersBefore: 1, lettersAfter: 2 })} />)
    expect(screen.getByText('孩子的手寫信件照片')).toBeInTheDocument()
    expect(screen.getByText(/1 張 → 2 張/)).toBeInTheDocument()
  })

  it('舊值或新值是空白：顯示「（空白）」而不是留空', () => {
    render(<ResubmitChanges changes={changes({ fields: [{ field: '願望描述', before: '', after: '想要一盒色鉛筆' }] })} />)
    expect(screen.getByText('（空白）')).toBeInTheDocument()
  })

  it('完全沒有變動：明說沒有變動', () => {
    render(<ResubmitChanges changes={changes({ fields: [] })} />)
    expect(screen.getByText('文字與信件照片都沒有變動。')).toBeInTheDocument()
  })

  it('沒有上次核准的紀錄（功能上線前的舊願望）：說明無法比較', () => {
    render(<ResubmitChanges changes={undefined} />)
    expect(screen.getByText(/無法比較改了什麼/)).toBeInTheDocument()
  })
})

describe('WishHistoryTimeline（願望歷程）', () => {
  const items: WishHistoryItem[] = [
    { type: 'SUBMITTED', label: '提交審核', at: '2026-10-01T01:00:00Z' },
    { type: 'APPROVED', label: '管理員核准', at: '2026-10-01T02:00:00Z' },
    { type: 'FROZEN', label: '管理員凍結', at: '2026-10-02T02:00:00Z', note: '信件照片露出真實姓名' },
    { type: 'FREEZE_LIFTED_BY_RESUBMIT', label: '因機構修改重新送審而解除凍結', at: '2026-10-03T02:00:00Z' },
  ]

  it('依序顯示每一筆，凍結等事件附上原因', () => {
    render(<WishHistoryTimeline items={items} />)
    const rows = screen.getAllByRole('listitem')
    expect(rows).toHaveLength(4)
    expect(rows[0]).toHaveTextContent('提交審核')
    expect(rows[2]).toHaveTextContent('管理員凍結')
    expect(rows[2]).toHaveTextContent('信件照片露出真實姓名')
    expect(rows[3]).toHaveTextContent('解除凍結')
  })

  it('沒有紀錄：說明是功能上線前建立的', () => {
    render(<WishHistoryTimeline items={[]} />)
    expect(screen.getByText(/紀錄功能上線之前/)).toBeInTheDocument()
  })
})
