// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import { uploadImage } from '../../lib/upload'
import type { WishOrgView } from '../../lib/types'
import { UnlockRequestDialog } from './UnlockRequestDialog'

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: {
    post: vi.fn(async () => ({ id: 1, status: 'PENDING', createdAt: '2026-10-22T00:00:00Z' })),
    get: vi.fn(async () => ({ ageRanges: [{ value: 'AGE_7_9', label: '7–9 歲' }] })),
  },
}))
vi.mock('../../lib/upload', () => ({
  uploadImage: vi.fn(async () => ({ id: 'att-1' })),
  validateImage: () => null,
}))
// 裁切畫面靠真實圖片與版面：換成按一下就回傳裁好的檔案
vi.mock('../../components/ui/ImageCropDialog', () => ({
  ImageCropDialog: ({ file, onConfirm }: { file: File | null; onConfirm: (cropped: File) => void }) => (
    file ? <button type="button" onClick={() => onConfirm(new File(['x'], 'cropped.jpg', { type: 'image/jpeg' }))}>確定裁切</button> : null
  ),
}))

const wish = {
  id: 'w1', title: '色鉛筆組', childAlias: '小星', ageRange: 'AGE_7_9', ageRangeLabel: '7–9 歲', description: '想要畫畫',
} as WishOrgView

beforeEach(() => {
  vi.mocked(api.post).mockClear()
  vi.mocked(uploadImage).mockClear()
  URL.createObjectURL = vi.fn(() => 'blob:fake')
  URL.revokeObjectURL = vi.fn()
})

describe('UnlockRequestDialog 的新信件照片', () => {
  it('選檔 → 裁切 → 送出申請時才上傳，並把新信件的附件 id 帶進 revision', async () => {
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={new QueryClient()}>
        <UnlockRequestDialog open wish={wish} canRevise onClose={vi.fn()} onDone={vi.fn()} />
      </QueryClientProvider>,
    )
    const dialog = within(await screen.findByRole('dialog', { hidden: true, name: '申請解除凍結' }))
    await user.upload(dialog.getByLabelText('新的信件照片（選填）', { selector: 'input' }),
      new File(['raw'], 'letter.png', { type: 'image/png' }))
    await user.click(await screen.findByRole('button', { name: '確定裁切', hidden: true }))
    expect(uploadImage).not.toHaveBeenCalled()   // 送出前不上傳

    await user.click(dialog.getByRole('button', { name: '送出申請', hidden: true }))
    await waitFor(() => expect(uploadImage).toHaveBeenCalledWith('WISH_LETTER_REVISION', 'w1', expect.any(File)))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      '/api/organizations/me/wishes/w1/unlock-request',
      { message: undefined, revision: { letterAttachmentId: 'att-1' } }))
  })

  it('不是內容疑慮的凍結沒有信件欄位', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <UnlockRequestDialog open wish={wish} canRevise={false} onClose={vi.fn()} onDone={vi.fn()} />
      </QueryClientProvider>,
    )
    await screen.findByRole('dialog', { hidden: true })
    expect(screen.queryByText('新的信件照片（選填）')).toBeNull()
  })
})
