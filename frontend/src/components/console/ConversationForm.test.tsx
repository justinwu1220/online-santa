// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ConversationForm } from './ConversationLog'

describe('ConversationForm 的停用狀態', () => {
  it('預設可以輸入與傳送（團隊對話不受影響）', () => {
    const onSend = vi.fn()
    render(<ConversationForm placeholder="說點什麼" onSend={onSend} />)
    fireEvent.change(screen.getByPlaceholderText('說點什麼'), { target: { value: '你好' } })
    expect(screen.getByRole('button', { name: '傳送' })).toBeEnabled()
    fireEvent.click(screen.getByRole('button', { name: '傳送' }))
    expect(onSend).toHaveBeenCalledWith('你好')
  })

  it('disabled：直接顯示說明，輸入框與傳送鈕都停用，不會送出', () => {
    const onSend = vi.fn()
    render(<ConversationForm placeholder="說點什麼" disabled notice="認領者出現違規行為，正在積極處理中。" onSend={onSend} />)
    expect(screen.getByRole('status')).toHaveTextContent('認領者出現違規行為，正在積極處理中。')
    expect(screen.getByPlaceholderText('說點什麼')).toBeDisabled()
    expect(screen.getByRole('button', { name: '傳送' })).toBeDisabled()
    expect(onSend).not.toHaveBeenCalled()
  })
})
