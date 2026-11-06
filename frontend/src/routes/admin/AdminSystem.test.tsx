// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { AdminAuditAction, AuditLogView } from '../../lib/types'
import { ACTION_LABELS, AuditPanel } from './AdminSystem'

const NEW_ACTIONS: AdminAuditAction[] = [
  'SUSPEND_USER', 'REACTIVATE_USER', 'EDIT_WISH_CONTENT', 'RELEASE_CLAIM_BY_ADMIN',
  'DECIDE_UNLOCK_REQUEST', 'NOTIFY_AFFECTED_DONORS', 'VIEW_SUSPENSION_RECORD', 'REDACT_MESSAGE', 'DELETE_WISH_LETTER',
]

const logs: AuditLogView[] = [
  ...NEW_ACTIONS.map((action, index) => ({
    id: index + 1, adminEmail: 'admin@example.com', action, targetType: 'USER' as const,
    detail: `第 ${index + 1} 筆`, occurredAt: '2026-10-22T02:00:00Z',
  })),
  // 後端新增了前端還不認識的動作：退回顯示動作代碼，不能是空白
  {
    id: 99, adminEmail: 'admin@example.com', action: 'SOMETHING_NEW' as AdminAuditAction, targetType: 'SYSTEM' as const,
    detail: '未知動作', occurredAt: '2026-10-22T02:00:00Z',
  },
]

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: { get: vi.fn(async () => ({ content: logs, totalElements: logs.length, totalPages: 1, number: 0, size: 30 })) },
}))

describe('管理員操作紀錄的動作名稱', () => {
  it('每個動作都有中文名稱', () => {
    for (const action of NEW_ACTIONS) {
      expect(ACTION_LABELS[action], action).toBeTruthy()
    }
  })

  it('表格的動作欄不會是空白：新動作顯示中文名稱，不認識的動作顯示代碼', async () => {
    render(<QueryClientProvider client={new QueryClient()}><AuditPanel /></QueryClientProvider>)
    const rows = await screen.findAllByRole('row')
    const bodyRows = rows.slice(1)
    expect(bodyRows).toHaveLength(logs.length)
    for (const row of bodyRows) {
      const action = within(row).getAllByRole('cell')[2]
      expect(action.textContent?.trim(), row.textContent ?? '').not.toBe('')
    }
    const actionCells = bodyRows.map((row) => within(row).getAllByRole('cell')[2].textContent)
    expect(actionCells).toContain('停權認領者')
    expect(actionCells).toContain('檢視停權處置資料')
    expect(actionCells).toContain('SOMETHING_NEW')
  })

  it('篩選下拉可以選到新的動作', async () => {
    render(<QueryClientProvider client={new QueryClient()}><AuditPanel /></QueryClientProvider>)
    const select = await screen.findByRole('combobox', { name: '依動作篩選' })
    expect(within(select).getByRole('option', { name: '釋出認領' })).toBeInTheDocument()
    // 遮蔽訊息的功能已移除：不再出現在篩選，但舊的稽核紀錄仍顯示中文名稱（見 ACTION_LABELS）
    expect(within(select).queryByRole('option', { name: '遮蔽對話訊息' })).toBeNull()
  })
})
