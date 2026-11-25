// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ProgressSentence, SledProgress } from './SledProgress'

describe('雪橇進度條', () => {
  it('進度與目標寫在 aria-label 裡，已完成段依比例', () => {
    const { container } = render(<SledProgress delivered={128} goal={500} size="lg" />)

    expect(screen.getByRole('img', { name: '已送出 128 份，目標 500 份' })).toBeTruthy()
    expect((container.querySelector('.sled-progress__fill') as HTMLElement).style.width).toBe('25.6%')
  })

  it('目標是 0（檔期還沒有願望）時不會算出 NaN', () => {
    const { container } = render(<SledProgress delivered={0} goal={0} />)

    expect((container.querySelector('.sled-progress__fill') as HTMLElement).style.width).toBe('0%')
  })
})

describe('進度的那一句話', () => {
  it('還沒達標：說還差幾份', () => {
    render(<ProgressSentence delivered={128} goal={500} />)

    expect(screen.getByText(/已送出/).textContent).toBe('已送出 128 份，再 372 份就達成 500 份')
  })

  it('達標之後改成另一句', () => {
    render(<ProgressSentence delivered={500} goal={500} />)

    expect(screen.getByText(/已送出/).textContent).toBe('已送出 500 份，達成 500 份目標！')
  })
})
