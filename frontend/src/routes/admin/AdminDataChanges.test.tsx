// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import { AdminDataChanges } from './AdminDataChanges'

const userTargets = [
  { targetType: 'USER', targetId: 'u1', label: 'donor@example.com', changeCount: 3, lastChangedAt: '2026-11-01T02:00:00Z' },
  { targetType: 'USER', targetId: 'u2', label: undefined, changeCount: 1, lastChangedAt: '2026-10-01T02:00:00Z' },
]
const orgTargets = [
  { targetType: 'ORGANIZATION', targetId: 'o1', label: '陽光之家', changeCount: 2, lastChangedAt: '2026-10-20T02:00:00Z' },
]
const changes = [
  {
    id: 9, targetType: 'USER', targetId: 'u1', targetLabel: 'donor@example.com',
    changes: [{ field: 'phone', before: '0912-000-000', after: '0912-345-678' }],
    actorUserId: 'u1', actorEmail: 'donor@example.com', actorRole: 'DONOR',
    source: 'SELF_SERVICE', occurredAt: '2026-11-01T02:00:00Z',
  },
]

function pageOf(content: unknown[]) {
  return { content, page: 0, size: 30, totalElements: content.length, totalPages: 1, hasNext: false }
}

async function defaultGet(path: string) {
  if (path.includes('/profile-changes/targets')) {
    return pageOf(path.includes('ORGANIZATION') ? orgTargets : userTargets)
  }
  return pageOf(changes)
}

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: { get: vi.fn(defaultGet) },
}))

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/admin/data-changes']}>
      <QueryClientProvider client={new QueryClient()}><AdminDataChanges /></QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('個資異動紀錄頁', () => {
  // 用大括號：箭頭函式直接回傳 mock 的話，vitest 會把回傳值當成 teardown，
  // 在測試結束時「呼叫」它一次（而且沒有參數）
  beforeEach(() => { vi.mocked(api.get).mockClear() })

  it('預設是認領者分頁，只列出有異動紀錄的對象與次數', async () => {
    renderPage()
    expect(await screen.findByText('donor@example.com')).toBeInTheDocument()
    expect(screen.getByText('3 次')).toBeInTheDocument()
    // 帳號已刪除時退回顯示 id
    expect(screen.getByText('u2')).toBeInTheDocument()
  })

  it('切到機構分頁換成機構清單', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('donor@example.com')

    await user.click(screen.getByRole('tab', { name: /機構/ }))

    expect(await screen.findByText('陽光之家')).toBeInTheDocument()
    expect(screen.queryByText('donor@example.com')).toBeNull()
  })

  it('清單本身不載入明細——那支每呼叫一次就寫一筆稽核；點開抽屜才載入', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('donor@example.com')

    // 這時只打過對象清單，沒有打明細
    expect(vi.mocked(api.get).mock.calls.every(([path]) => path.includes('/targets'))).toBe(true)

    await user.click(screen.getByText('donor@example.com'))

    expect(await screen.findByText('聯絡電話')).toBeInTheDocument()
    const detailCalls = vi.mocked(api.get).mock.calls
      .filter(([path]) => !path.includes('/targets'))
    expect(detailCalls).toHaveLength(1)
    expect(detailCalls[0][0]).toContain('targetId=u1')
  })

  it('沒有任何對象時說明清楚，不是空白', async () => {
    // 只蓋掉這一次的對象清單回應，不動到其他測試
    vi.mocked(api.get).mockImplementationOnce(async () => pageOf([]))
    renderPage()
    expect(await screen.findByText('還沒有認領者修改過自己的資料。')).toBeInTheDocument()
  })

  it('抽屜標題是對象名稱，並標示對象別', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByText('donor@example.com'))

    const drawer = screen.getByRole('dialog', { hidden: true })
    expect(within(drawer).getByText('認領者')).toBeInTheDocument()
  })
})
