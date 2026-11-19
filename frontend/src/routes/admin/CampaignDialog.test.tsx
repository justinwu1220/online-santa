// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CampaignDialog } from './CampaignDialog'

const post = vi.fn()

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: { get: vi.fn(), post: (path: string, body?: unknown) => post(path, body), put: vi.fn() },
}))

function renderDialog() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <CampaignDialog campaign="new" onClose={() => {}} />
    </QueryClientProvider>,
  )
}

async function fill(label: string, value: string) {
  const input = screen.getByLabelText(new RegExp('^' + label)) as HTMLInputElement
  await userEvent.clear(input)
  await userEvent.type(input, value)
}

async function fillValid() {
  await fill('檔期名稱', '線上聖誕老人 2026')
  await fill('勸募許可文號', '衛部救字第1150000號')
  await fill('勸募開始日', '2026-11-25')
  await fill('勸募結束日', '2027-01-25')
  await fill('財物使用開始日', '2026-11-25')
  await fill('財物使用結束日', '2027-01-25')
  await fill('願望開始上架日', '2026-11-25')
  await fill('願望結束上架日', '2027-01-10')
}

beforeEach(() => { post.mockReset() })

describe('新增活動檔期表單', () => {
  it('勸募許可文號必填', async () => {
    renderDialog()
    await fillValid()
    await fill('勸募許可文號', ' ')

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('請填寫勸募許可文號')).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it('願望結束上架日必須早於勸募結束日', async () => {
    renderDialog()
    await fillValid()
    await fill('願望結束上架日', '2027-01-25')

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText(/願望結束上架日必須早於勸募結束日/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it('願望結束上架日不能早於開始上架日', async () => {
    renderDialog()
    await fillValid()
    await fill('願望結束上架日', '2026-11-01')

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('願望結束上架日不能早於開始上架日')).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it('資料正確：送出含上架期間，沒有統計截止日', async () => {
    post.mockResolvedValue({})
    renderDialog()
    await fillValid()

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() => expect(post).toHaveBeenCalled())
    const [path, body] = post.mock.calls[0]
    expect(path).toBe('/api/admin/campaigns')
    expect(body).toMatchObject({ wishListStart: '2026-11-25', wishListEnd: '2027-01-10', permitNo: '衛部救字第1150000號' })
    expect(body).not.toHaveProperty('cutoffDate')
    expect(screen.queryByLabelText(/統計截止日/)).toBeNull()
  })
})
