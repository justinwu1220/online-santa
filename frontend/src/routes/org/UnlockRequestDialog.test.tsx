// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import type { WishOrgView } from '../../lib/types'
import { UnlockRequestDialog } from './UnlockRequestDialog'

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: {
    post: vi.fn(async () => ({ id: 1, status: 'PENDING', createdAt: '2026-10-22T00:00:00Z' })),
    get: vi.fn(async () => ({ ageRanges: [{ value: 'AGE_7_9', label: '7–9 歲' }, { value: 'AGE_4_6', label: '4–6 歲' }] })),
  },
}))

const wish = {
  id: 'w1', title: '色鉛筆組', childAlias: '王小明在大安國小', ageRange: 'AGE_7_9', ageRangeLabel: '7–9 歲',
  description: '想要畫畫',
} as WishOrgView

function renderDialog(canRevise: boolean, onDone = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <UnlockRequestDialog open wish={wish} canRevise={canRevise} onClose={vi.fn()} onDone={onDone} />
    </QueryClientProvider>,
  )
  return onDone
}

describe('UnlockRequestDialog（機構申請解除凍結）', () => {
  beforeEach(() => vi.mocked(api.post).mockClear())

  it('內容疑慮：只把改過的欄位送出，沒有禮物（標題）欄位', async () => {
    const user = userEvent.setup()
    const onDone = renderDialog(true)
    const dialog = within(await screen.findByRole('dialog', { hidden: true, name: '申請解除凍結' }))

    expect(dialog.queryByLabelText(/願望標題|禮物/)).toBeNull()
    const alias = dialog.getByLabelText('孩子的暱稱')
    await user.clear(alias)
    await user.type(alias, '小星')
    await user.type(dialog.getByLabelText(/給管理員的說明/), '已改成化名')
    await user.click(dialog.getByRole('button', { name: '送出申請', hidden: true }))

    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      '/api/organizations/me/wishes/w1/unlock-request',
      { message: '已改成化名', revision: { childAlias: '小星' } }))
    await waitFor(() => expect(onDone).toHaveBeenCalled())
  })

  it('沒有修改任何欄位：不帶 revision，只送說明', async () => {
    const user = userEvent.setup()
    renderDialog(true)
    const dialog = within(await screen.findByRole('dialog', { hidden: true, name: '申請解除凍結' }))
    await user.click(dialog.getByRole('button', { name: '送出申請', hidden: true }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      '/api/organizations/me/wishes/w1/unlock-request', { message: undefined, revision: undefined }))
  })

  it('不是內容疑慮的凍結：沒有修改欄位，只能說明並申請', async () => {
    renderDialog(false)
    const dialog = within(await screen.findByRole('dialog', { hidden: true, name: '申請解除凍結' }))
    expect(dialog.queryByLabelText('孩子的暱稱')).toBeNull()
    expect(dialog.getByText(/不能修改內容/)).toBeInTheDocument()
  })
})
