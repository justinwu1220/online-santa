// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ConversationForm } from './ConversationLog'

describe('ConversationForm（打到一半的訊息）', () => {
  it('有 draftKey：元件卸載（關掉抽屜）再掛載，訊息還在；傳送成功後清掉', async () => {
    const onSend = vi.fn().mockResolvedValue(undefined)
    const first = render(<ConversationForm placeholder="說點什麼" draftKey="chat:test" onSend={onSend} />)
    fireEvent.change(screen.getByPlaceholderText('說點什麼'), { target: { value: '還沒傳的話' } })
    first.unmount()

    render(<ConversationForm placeholder="說點什麼" draftKey="chat:test" onSend={onSend} />)
    expect(screen.getByPlaceholderText('說點什麼')).toHaveValue('還沒傳的話')

    fireEvent.click(screen.getByRole('button', { name: '傳送' }))
    await waitFor(() => expect(onSend).toHaveBeenCalledWith('還沒傳的話'))
    await waitFor(() => expect(screen.getByPlaceholderText('說點什麼')).toHaveValue(''))
  })

  it('不同對象的草稿互不影響', () => {
    const a = render(<ConversationForm placeholder="說點什麼" draftKey="chat:x" onSend={vi.fn()} />)
    fireEvent.change(screen.getByPlaceholderText('說點什麼'), { target: { value: 'X 的話' } })
    a.unmount()
    render(<ConversationForm placeholder="說點什麼" draftKey="chat:y" onSend={vi.fn()} />)
    expect(screen.getByPlaceholderText('說點什麼')).toHaveValue('')
  })
})
