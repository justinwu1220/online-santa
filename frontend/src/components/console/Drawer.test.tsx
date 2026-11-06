// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Drawer } from './Drawer'

function dialogEl() {
  return document.querySelector('dialog') as HTMLDialogElement
}

describe('Drawer（關閉的時機）', () => {
  it('自己的 Esc（cancel）會呼叫 onClose', () => {
    const onClose = vi.fn()
    render(<Drawer open onClose={onClose} labelledBy="t" title="標題"><p>內容</p></Drawer>)
    const cancel = new Event('cancel', { cancelable: true })
    dialogEl().dispatchEvent(cancel)
    expect(cancel.defaultPrevented).toBe(true)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('在抽屜內容裡按下、移到背景才放開不會關；背景上按下並放開才關', () => {
    const onClose = vi.fn()
    render(<Drawer open onClose={onClose} labelledBy="t" title="標題"><p>內容</p></Drawer>)
    fireEvent.mouseDown(screen.getByText('內容'))
    fireEvent.click(dialogEl())
    expect(onClose).not.toHaveBeenCalled()

    fireEvent.mouseDown(dialogEl())
    fireEvent.click(dialogEl())
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('裡面的檔案選擇器按取消（會冒泡的 cancel）不會關掉抽屜', () => {
    const onClose = vi.fn()
    render(
      <Drawer open onClose={onClose} labelledBy="t" title="標題">
        <input type="file" aria-label="選檔" />
      </Drawer>,
    )
    screen.getByLabelText('選檔').dispatchEvent(new Event('cancel', { bubbles: true, cancelable: true }))
    expect(onClose).not.toHaveBeenCalled()
  })
})
