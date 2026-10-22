// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { CurrentUser } from '../../lib/types'
import { SuspendedBar } from './SuspendedBar'

let current: Partial<CurrentUser> | undefined
vi.mock('../../lib/useCurrentUser', () => ({ useCurrentUser: () => ({ data: current }) }))

describe('SuspendedBar（認領者停權提示）', () => {
  it('被停權：顯示原因、受限範圍與聯絡信箱', () => {
    current = { suspended: true, suspendedReason: '對機構騷擾' }
    render(<SuspendedBar />)
    expect(screen.getByRole('status')).toHaveTextContent('你的帳號目前受到限制')
    expect(screen.getByRole('status')).toHaveTextContent('對機構騷擾')
    expect(screen.getByRole('status')).toHaveTextContent('onlinesanta2026@gmail.com')
  })

  it('沒有停權或還沒登入：不顯示', () => {
    current = { suspended: false }
    const { container, rerender } = render(<SuspendedBar />)
    expect(container).toBeEmptyDOMElement()
    current = undefined
    rerender(<SuspendedBar />)
    expect(container).toBeEmptyDOMElement()
  })
})
