// @vitest-environment jsdom
import { renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useScrollLock } from './scrollLock'

const root = () => document.documentElement

describe('useScrollLock', () => {
  it('open 期間把頁面設成不可捲動，關閉後還原', () => {
    const { rerender } = renderHook(({ open }) => useScrollLock(open), { initialProps: { open: false } })
    expect(root().style.overflow).toBe('')

    rerender({ open: true })
    expect(root().style.overflow).toBe('hidden')

    rerender({ open: false })
    expect(root().style.overflow).toBe('')
  })

  it('多個彈窗疊在一起：內層關掉不會提早解鎖，全部關掉才解鎖', () => {
    const outer = renderHook(() => useScrollLock(true))
    const inner = renderHook(() => useScrollLock(true))
    expect(root().style.overflow).toBe('hidden')

    inner.unmount()
    expect(root().style.overflow).toBe('hidden')

    outer.unmount()
    expect(root().style.overflow).toBe('')
  })

  it('元件在開著的時候被卸載（例如換頁）也會解鎖，不會卡住捲動', () => {
    const view = renderHook(() => useScrollLock(true))
    expect(root().style.overflow).toBe('hidden')
    view.unmount()
    expect(root().style.overflow).toBe('')
  })

  it('鎖定前原本就有的行內樣式會被還原，而不是被清掉', () => {
    root().style.overflow = 'scroll'
    const view = renderHook(() => useScrollLock(true))
    expect(root().style.overflow).toBe('hidden')
    view.unmount()
    expect(root().style.overflow).toBe('scroll')
  })
})
