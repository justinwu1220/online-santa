// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { pageTitle } from '../lib/brand'
import { EnvironmentBanner } from './EnvironmentBanner'

afterEach(() => vi.unstubAllEnvs())

describe('EnvironmentBanner（非正式環境的醒目提示）', () => {
  it('正式站沒有設定環境名稱：什麼都不顯示', () => {
    vi.stubEnv('VITE_ENV_LABEL', '')
    const { container } = render(<EnvironmentBanner />)

    expect(container).toBeEmptyDOMElement()
    expect(screen.queryByTestId('environment-banner')).not.toBeInTheDocument()
  })

  it('完全沒有這個環境變數（正式站的建置）：什麼都不顯示', () => {
    vi.stubEnv('VITE_ENV_LABEL', undefined as unknown as string)
    const { container } = render(<EnvironmentBanner />)

    expect(container).toBeEmptyDOMElement()
  })

  it('只有空白也視為沒設定', () => {
    vi.stubEnv('VITE_ENV_LABEL', '   ')
    const { container } = render(<EnvironmentBanner />)

    expect(container).toBeEmptyDOMElement()
  })

  it('測試站：顯示環境名稱與「不要填真實個資」的警語', () => {
    vi.stubEnv('VITE_ENV_LABEL', '測試站')
    render(<EnvironmentBanner />)

    const banner = screen.getByTestId('environment-banner')
    expect(banner).toHaveTextContent('這裡是測試站')
    expect(banner).toHaveTextContent('請勿填入真實個資')
    expect(banner).toHaveAttribute('role', 'note')
  })

  it('黏在頂端，捲動時也看得到', () => {
    vi.stubEnv('VITE_ENV_LABEL', '測試站')
    render(<EnvironmentBanner />)

    expect(screen.getByTestId('environment-banner')).toHaveStyle({ position: 'sticky', top: '0px' })
  })
})

describe('pageTitle（分頁標題的環境前綴）', () => {
  it('正式站：維持原本的標題', () => {
    vi.stubEnv('VITE_ENV_LABEL', '')
    expect(pageTitle()).toBe('線上聖誕老人 × 社團法人彰化縣康乃心關懷協會 2026')
    expect(pageTitle('登入')).toBe('登入 — 線上聖誕老人')
  })

  it('測試站：每一頁的標題最前面都加上 [測試站]', () => {
    vi.stubEnv('VITE_ENV_LABEL', '測試站')
    expect(pageTitle()).toBe('[測試站] 線上聖誕老人 × 社團法人彰化縣康乃心關懷協會 2026')
    expect(pageTitle('登入')).toBe('[測試站] 登入 — 線上聖誕老人')
  })
})
