// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Chat } from './Chat'

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: { get: vi.fn(async () => []), post: vi.fn() },
}))

function renderChat(props: Partial<React.ComponentProps<typeof Chat>> = {}) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <Chat claimId="c1" closed={false} counterpart="陽光之家" {...props} />
    </QueryClientProvider>,
  )
}

describe('Chat（認領者與機構的對話）', () => {
  it('一般狀態可以輸入', async () => {
    renderChat()
    expect(await screen.findByLabelText('訊息內容')).toBeEnabled()
  })

  it('這筆認領被凍結或停權時，一打開就顯示說明並停用輸入與傳送，不用等按下傳送', async () => {
    renderChat({ restriction: '此機構已被管理員暫時停權，正在積極處理中。' })
    expect(await screen.findByLabelText('訊息內容')).toBeDisabled()
    expect(screen.getByText('此機構已被管理員暫時停權，正在積極處理中。')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '傳送' })).toBeDisabled()
  })

  it('認領已結束時維持原本的結束說明，沒有輸入框', async () => {
    renderChat({ closed: true, restriction: '不應該出現' })
    expect(await screen.findByText('這筆認領已經結束，無法再傳送訊息。')).toBeInTheDocument()
    expect(screen.queryByLabelText('訊息內容')).toBeNull()
  })
})
