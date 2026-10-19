// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { AttachmentView } from '../../lib/types'
import { ClaimProofs } from './ClaimProofs'

const file = (id: string, purpose: AttachmentView['purpose'], uploadedAt: string): AttachmentView =>
  ({ id, purpose, url: `/${id}.jpg`, contentType: 'image/jpeg', uploadedAt })

const group = (title: string) => screen.getByText(title).closest('.att-group') as HTMLElement

describe('ClaimProofs（管理員的寄件與收件證明）', () => {
  it('依用途拆成兩組，各自標示上傳者與張數', () => {
    render(<ClaimProofs donorName="小明" organizationName="星光之家" received
      files={[
        file('a', 'SHIPPING_PROOF', '2026-10-01T02:00:00Z'),
        file('b', 'SHIPPING_PROOF', '2026-10-01T03:00:00Z'),
        file('c', 'ORG_FEEDBACK', '2026-10-05T02:00:00Z'),
      ]} />)
    expect(group('認領者寄件證明')).toHaveTextContent('小明 上傳')
    expect(group('認領者寄件證明')).toHaveTextContent('2 張')
    expect(group('機構收件證明')).toHaveTextContent('星光之家 上傳')
    expect(group('機構收件證明')).toHaveTextContent('1 張')
    // 縮圖各自歸組，放大時不會混在一起
    expect(group('認領者寄件證明').querySelectorAll('.thumb')).toHaveLength(2)
    expect(group('機構收件證明').querySelectorAll('.thumb')).toHaveLength(1)
  })

  it('機構還沒確認收到：收件證明顯示尚未確認', () => {
    render(<ClaimProofs donorName="小明" organizationName="星光之家" received={false}
      files={[file('a', 'SHIPPING_PROOF', '2026-10-01T02:00:00Z')]} />)
    expect(group('機構收件證明')).toHaveTextContent('機構還沒確認收到。')
    expect(group('機構收件證明').querySelector('.thumb')).toBeNull()
  })

  it('機構已確認收到但沒附照片：說明沒有附', () => {
    render(<ClaimProofs donorName="小明" organizationName="星光之家" received
      files={[file('a', 'SHIPPING_PROOF', '2026-10-01T02:00:00Z')]} />)
    expect(group('機構收件證明')).toHaveTextContent('機構確認收到時沒有附照片。')
  })
})
