// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { UnlockLetterCompare } from './AdminWishes'

/**
 * 守的是版面契約，不是外觀：`.preview-zoom` 是 `position: absolute; inset: 0`，
 * 每一張都得被有尺寸的 `.dletter` 框住。曾經少了這層框，兩張信件就逃到抽屜的
 * `<dialog>` 上變成蓋住願望資訊的大圖。vitest 設了 `css: false`、jsdom 也不算版面，
 * 所以只能從結構確認框還在。
 */
describe('UnlockLetterCompare（解除凍結申請的信件比對）', () => {
  it('每一張預覽都包在 .dletter 定位框裡', () => {
    const { container } = render(
      <UnlockLetterCompare currentUrl="https://cdn.test/old.jpg" nextUrl="https://cdn.test/new.jpg" masked={false} />,
    )

    const frames = container.querySelectorAll('.letter-compare figure > .dletter')
    expect(frames).toHaveLength(2)

    const zooms = container.querySelectorAll('.preview-zoom')
    expect(zooms).toHaveLength(2)
    zooms.forEach((zoom) => expect(zoom.closest('.dletter')).not.toBeNull())
  })

  it('目前沒有信件：那一格顯示佔位字，新信件仍有定位框', () => {
    const { container } = render(
      <UnlockLetterCompare currentUrl={undefined} nextUrl="https://cdn.test/new.jpg" masked={false} />,
    )

    expect(screen.getByText('（已刪除或沒有信件）')).toBeInTheDocument()
    expect(container.querySelectorAll('.letter-compare figure > .dletter')).toHaveLength(2)
    const zoom = container.querySelector('.preview-zoom')
    expect(zoom).not.toBeNull()
    expect(zoom!.closest('.dletter')).not.toBeNull()
  })

  it('信件被屏蔽：標題標註「已屏蔽」', () => {
    render(<UnlockLetterCompare currentUrl="https://cdn.test/old.jpg" nextUrl="https://cdn.test/new.jpg" masked />)
    expect(screen.getByText('目前的信件（已屏蔽）')).toBeInTheDocument()
  })
})
