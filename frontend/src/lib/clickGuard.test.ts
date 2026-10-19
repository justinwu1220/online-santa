// @vitest-environment jsdom
import { fireEvent } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { installClickGuard } from './clickGuard'

let main: HTMLElement
let off: () => void
let warn: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  document.body.innerHTML = '<div class="org-main"><button id="inside">按鈕</button></div>'
  main = document.querySelector('.org-main') as HTMLElement
  off = installClickGuard(() => main)
  warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
})
afterEach(() => { off(); warn.mockRestore() })

function openDialog(style = '') {
  const dialog = document.createElement('dialog')
  dialog.className = 'drawer'
  dialog.setAttribute('style', style)
  document.body.append(dialog)
  dialog.showModal()
  return dialog
}

describe('installClickGuard（右側內容點不動的保險）', () => {
  it('看不見（透明）的開啟中彈窗接走點擊：記錄並關掉它', () => {
    const dialog = openDialog('opacity:0')
    fireEvent.pointerDown(dialog)
    expect(dialog.open).toBe(false)
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('[click-guard]'), expect.anything())
  })

  it('關閉動畫卡住超過 1 秒的彈窗也會被關掉', () => {
    vi.useFakeTimers()
    const dialog = openDialog()
    dialog.classList.add('is-closing')
    fireEvent.pointerDown(dialog)          // 第一次看到：還在正常的關閉動畫時間內
    expect(dialog.open).toBe(true)
    vi.advanceTimersByTime(1500)
    fireEvent.pointerDown(dialog)
    expect(dialog.open).toBe(false)
    vi.useRealTimers()
  })

  it('看得見的開啟中抽屜：不動它', () => {
    const dialog = openDialog('opacity:1')
    fireEvent.pointerDown(dialog)
    expect(dialog.open).toBe(true)
    expect(warn).not.toHaveBeenCalled()
  })

  it('點在右側內容裡的正常元素：什麼都不做', () => {
    fireEvent.pointerDown(document.getElementById('inside')!)
    expect(warn).not.toHaveBeenCalled()
  })

  it('提供 __consoleDiag 讓卡住時能直接取得現場報告，解除後移除', () => {
    const diag = (window as unknown as { __consoleDiag: () => { dialogs: unknown[] } }).__consoleDiag
    expect(diag().dialogs).toEqual([])
    off()
    expect((window as unknown as { __consoleDiag?: unknown }).__consoleDiag).toBeUndefined()
    off = installClickGuard(() => main)
  })
})
