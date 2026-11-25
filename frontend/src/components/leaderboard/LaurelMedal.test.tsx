// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { LaurelMedal } from './LaurelMedal'

describe('名次的桂冠環', () => {
  it('環心寫名次數字，名次也寫在 aria-label 裡', () => {
    render(<LaurelMedal rank={2} />)

    const medal = screen.getByRole('img', { name: '第 2 名' })
    expect(medal.textContent).toBe('2')
  })

  it('第 4 名以後不會用到，真的傳進來就當第 3 名的樣子，不會畫出空環', () => {
    render(<LaurelMedal rank={7} />)

    expect(screen.getByRole('img', { name: '第 7 名' }).textContent).toBe('3')
  })
})
