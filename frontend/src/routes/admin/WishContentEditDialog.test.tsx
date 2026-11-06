// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { AdminWishDetail } from '../../lib/types'
import { WishContentEditDialog } from './WishContentEditDialog'

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: { get: vi.fn(async () => ({ ageRanges: [{ value: 'AGE_4_6', label: '4–6 歲' }], categories: [], organizations: [] })) },
}))

const wish = { title: '色鉛筆組', childAlias: '王小明在大安國小', ageRangeLabel: '7–9 歲', description: '想要畫畫' } as AdminWishDetail

function renderDialog(alreadyFrozen: boolean, onSubmit = vi.fn(async () => {})) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <WishContentEditDialog open wish={wish} alreadyFrozen={alreadyFrozen} onClose={vi.fn()} onSubmit={onSubmit} />
    </QueryClientProvider>,
  )
  return onSubmit
}

describe('WishContentEditDialog（凍結的唯一入口）', () => {
  it('未凍結：修改欄位選填，沒改欄位只填原因也能凍結', async () => {
    const user = userEvent.setup()
    const onSubmit = renderDialog(false)
    const dialog = within(await screen.findByRole('dialog', { hidden: true }))

    expect(dialog.getByRole('heading', { name: '凍結這個願望', hidden: true })).toBeInTheDocument()
    expect(dialog.queryByRole('checkbox', { hidden: true })).toBeNull()
    await user.type(dialog.getByLabelText(/凍結原因/), '信件照片露出孩子的真實姓名')
    await user.click(dialog.getByRole('button', { name: '凍結', hidden: true }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ reason: '信件照片露出孩子的真實姓名', freeze: true }))
  })

  it('未凍結：有改的欄位才帶進送出的內容，而且沒有禮物（標題）欄位', async () => {
    const user = userEvent.setup()
    const onSubmit = renderDialog(false)
    const dialog = within(await screen.findByRole('dialog', { hidden: true }))

    expect(dialog.queryByLabelText(/願望標題|禮物/)).toBeNull()
    const alias = dialog.getByLabelText('孩子的暱稱')
    await user.clear(alias)
    await user.type(alias, '小星')
    await user.type(dialog.getByLabelText(/凍結原因/), '暱稱含真實姓名與學校')
    await user.click(dialog.getByRole('button', { name: '凍結', hidden: true }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ childAlias: '小星', reason: '暱稱含真實姓名與學校', freeze: true }))
  })

  it('有信件照片才出現「屏蔽信件照片」，預設不勾；勾了會帶 maskLetter，已凍結時只屏蔽也算有修改', async () => {
    const user = userEvent.setup()
    const withLetter = { ...wish, letterPhotos: [{ id: 'a1' }] } as unknown as AdminWishDetail
    const onSubmit = vi.fn(async () => {})
    render(
      <QueryClientProvider client={new QueryClient()}>
        <WishContentEditDialog open wish={withLetter} alreadyFrozen onClose={vi.fn()} onSubmit={onSubmit} />
      </QueryClientProvider>,
    )
    const dialog = within(await screen.findByRole('dialog', { hidden: true }))
    const mask = dialog.getByRole('checkbox', { hidden: true })
    expect(mask).not.toBeChecked()
    await user.type(dialog.getByLabelText(/修改原因/), '查看後發現信件含個資')
    await user.click(mask)
    await user.click(dialog.getByRole('button', { name: '儲存修改', hidden: true }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ reason: '查看後發現信件含個資', freeze: false, maskLetter: true }))
  })

  it('信件已經被屏蔽就不再出現勾選', async () => {
    const masked = { ...wish, letterPhotos: [{ id: 'a1' }], letterMasked: true } as unknown as AdminWishDetail
    render(
      <QueryClientProvider client={new QueryClient()}>
        <WishContentEditDialog open wish={masked} alreadyFrozen onClose={vi.fn()} onSubmit={vi.fn()} />
      </QueryClientProvider>,
    )
    const dialog = within(await screen.findByRole('dialog', { hidden: true }))
    expect(dialog.queryByRole('checkbox', { hidden: true })).toBeNull()
  })

  it('已凍結：只修改內容，不再凍結；至少要改一個欄位', async () => {
    const user = userEvent.setup()
    const onSubmit = renderDialog(true)
    const dialog = within(await screen.findByRole('dialog', { hidden: true }))

    expect(dialog.getByRole('heading', { name: '修改內容', hidden: true })).toBeInTheDocument()
    await user.type(dialog.getByLabelText(/修改原因/), '再清掉說明裡的學校名稱')
    await user.click(dialog.getByRole('button', { name: '儲存修改', hidden: true }))
    expect(await dialog.findByText('沒有修改任何欄位')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()

    const description = dialog.getByLabelText(/願望說明/)
    await user.clear(description)
    await user.type(description, '想畫畫')
    await user.click(dialog.getByRole('button', { name: '儲存修改', hidden: true }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ description: '想畫畫', reason: '再清掉說明裡的學校名稱', freeze: false }))
  })
})
