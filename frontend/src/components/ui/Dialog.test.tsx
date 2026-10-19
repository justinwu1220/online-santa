// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Dialog } from './Dialog'

function dialogEl() {
  return document.querySelector('dialog') as HTMLDialogElement
}

describe('Dialog', () => {
  it('open 為 true 時開啟；同時鎖住頁面捲動，關閉後解鎖', async () => {
    const { rerender } = render(<Dialog open onClose={() => {}}><p>內容</p></Dialog>)
    expect(dialogEl()).toHaveAttribute('open')
    expect(document.documentElement.style.overflow).toBe('hidden')

    rerender(<Dialog open={false} onClose={() => {}}><p>內容</p></Dialog>)
    await waitFor(() => expect(dialogEl()).not.toHaveAttribute('open'), { timeout: 1000 })
    expect(document.documentElement.style.overflow).toBe('')
  })

  it('Esc（cancel）時交給外層決定：可略過的視窗會呼叫 onClose，視窗本身先不關', () => {
    const onClose = vi.fn()
    render(<Dialog open onClose={onClose}><p>內容</p></Dialog>)
    const cancel = new Event('cancel', { cancelable: true })
    dialogEl().dispatchEvent(cancel)
    expect(cancel.defaultPrevented).toBe(true)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('不可略過的視窗（dismissible=false）：Esc 與點背景都不會關', () => {
    const onClose = vi.fn()
    render(<Dialog open dismissible={false} onClose={onClose}><p>內容</p></Dialog>)
    dialogEl().dispatchEvent(new Event('cancel', { cancelable: true }))
    fireEvent.click(dialogEl())
    expect(onClose).not.toHaveBeenCalled()
  })

  it('不可略過的視窗被瀏覽器的第二次 Esc 直接關掉時，會立刻重新打開', async () => {
    render(<Dialog open dismissible={false} onClose={() => {}}><p>首次說明</p></Dialog>)
    expect(dialogEl()).toHaveAttribute('open')

    // 瀏覽器防濫用規則：沒有 cancel 事件、直接關掉
    dialogEl().close()
    await waitFor(() => expect(dialogEl()).toHaveAttribute('open'))
  })

  it('可略過的視窗被原生關掉時，通知外層同步狀態（不重開）', async () => {
    const onClose = vi.fn()
    render(<Dialog open onClose={onClose}><p>內容</p></Dialog>)
    dialogEl().close()
    expect(onClose).toHaveBeenCalledTimes(1)
    await Promise.resolve()
    expect(dialogEl()).not.toHaveAttribute('open')
  })

  it('點到視窗本身（背景區）才關，點裡面的內容不關', () => {
    const onClose = vi.fn()
    render(<Dialog open onClose={onClose}><p>內容</p></Dialog>)
    fireEvent.click(screen.getByText('內容'))
    expect(onClose).not.toHaveBeenCalled()
    fireEvent.click(dialogEl())
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
