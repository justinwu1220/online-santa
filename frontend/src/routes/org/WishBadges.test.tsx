// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { WishOrgView } from '../../lib/types'
import { WishBadges } from './OrgWishes'

const base = { status: 'AVAILABLE', stage: 'OPEN', frozen: false, unlockRequested: false } as WishOrgView

describe('WishBadges（機構願望清單的徽章，與管理員清單一致）', () => {
  it('沒有特殊狀況時不顯示徽章', () => {
    const { container } = render(<WishBadges wish={base} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('凍結、申請解除、逾期未寄可以同時顯示', () => {
    render(<WishBadges wish={{ ...base, status: 'CLAIMED', stage: 'CLAIMED_OVERDUE', frozen: true, unlockRequested: true }} />)
    expect(screen.getByText('已凍結')).toBeInTheDocument()
    expect(screen.getByText('申請解除')).toBeInTheDocument()
    expect(screen.getByText('逾期未寄')).toBeInTheDocument()
  })

  it('曾經上架過又送審的願望標「修改後重審」；第一次送審不標', () => {
    const { rerender } = render(<WishBadges wish={{ ...base, status: 'PENDING_REVIEW', stage: 'PENDING_REVIEW', publishedAt: '2026-10-01T00:00:00Z' }} />)
    expect(screen.getByText('修改後重審')).toBeInTheDocument()
    rerender(<WishBadges wish={{ ...base, status: 'PENDING_REVIEW', stage: 'PENDING_REVIEW' }} />)
    expect(screen.queryByText('修改後重審')).toBeNull()
  })
})
