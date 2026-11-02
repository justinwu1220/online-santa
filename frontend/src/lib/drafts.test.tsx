// @vitest-environment jsdom
import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { clearDraft, hasDraft, useDraftState } from './drafts'

describe('useDraftState（關閉再打開保留輸入）', () => {
  it('改過的內容在元件重新掛載後讀得回來；沒有 key 就不存', () => {
    const first = renderHook(() => useDraftState('t:a', ''))
    act(() => first.result.current[1]('打到一半'))
    first.unmount()

    expect(hasDraft('t:a')).toBe(true)
    expect(renderHook(() => useDraftState('t:a', '')).result.current[0]).toBe('打到一半')

    const noKey = renderHook(() => useDraftState(undefined, ''))
    act(() => noKey.result.current[1]('不存'))
    noKey.unmount()
    expect(renderHook(() => useDraftState(undefined, '')).result.current[0]).toBe('')
    clearDraft('t:a')
  })

  it('不同對象的草稿互不影響；key 換了就改讀那個 key 的草稿', () => {
    const view = renderHook(({ id }) => useDraftState(`t:${id}`, ''), { initialProps: { id: 'x' } })
    act(() => view.result.current[1]('X 的草稿'))
    view.rerender({ id: 'y' })
    expect(view.result.current[0]).toBe('')
    act(() => view.result.current[1]('Y 的草稿'))
    view.rerender({ id: 'x' })
    expect(view.result.current[0]).toBe('X 的草稿')
    clearDraft('t:x'); clearDraft('t:y')
  })

  it('reset 會丟掉草稿並帶入新的內容；clearDraft 之後重新掛載回到初始值', () => {
    const view = renderHook(() => useDraftState('t:r', 'init'))
    act(() => view.result.current[1]('改過'))
    act(() => view.result.current[2]('重設'))
    expect(view.result.current[0]).toBe('重設')
    expect(hasDraft('t:r')).toBe(false)

    act(() => view.result.current[1]('又改'))
    clearDraft('t:r')
    view.unmount()
    expect(renderHook(() => useDraftState('t:r', 'init')).result.current[0]).toBe('init')
  })

  it('setter 也能用函式更新', () => {
    const view = renderHook(() => useDraftState('t:f', { a: 1 }))
    act(() => view.result.current[1]((current) => ({ ...current, a: current.a + 1 })))
    expect(view.result.current[0]).toEqual({ a: 2 })
    clearDraft('t:f')
  })
})
