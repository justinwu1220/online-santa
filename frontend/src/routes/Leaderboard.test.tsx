// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Leaderboard } from './Leaderboard'

/** 回 52 位：前三名之後都是 1 份，用來驗「一次 20 位、再看 20 位」。 */
function board() {
  const entries = [
    { rank: 1, name: '送最多的人', delivered: 12, tier: 'LEGEND', anonymous: false },
    { rank: 2, name: '匿名的聖誕老人', delivered: 9, tier: 'CAPTAIN', anonymous: true },
    { rank: 3, name: '大樹', delivered: 8, tier: 'CAPTAIN', anonymous: false },
    ...Array.from({ length: 49 }, (_, i) => ({
      rank: 4, name: `第四名並列 ${i}`, delivered: 1, tier: 'ELF', anonymous: false,
    })),
  ]
  return { campaignName: '測試檔期', goal: 500, delivered: 128, donorCount: entries.length, entries }
}

const me = {
  ranked: true, delivered: 8, rank: 3, lifetimeDelivered: 8,
  tier: 'CAPTAIN', nextTier: 'LEGEND', toNextTier: 2, showName: true,
}

const get = vi.fn()
const put = vi.fn()

vi.mock('../lib/api', async (original) => ({
  ...(await original<object>()),
  api: {
    get: (path: string, init?: RequestInit) => get(path, init),
    put: (path: string, body: unknown) => put(path, body),
  },
}))

vi.mock('../lib/authContext', async (original) => ({
  ...(await original<object>()),
  useAuth: () => ({ email: 'me@example.com', loading: false }),
}))

function renderPage(state?: { intro: boolean }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/leaderboard', state }]}>
      <QueryClientProvider client={client}>
        <Leaderboard />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('排行榜頁', () => {
  beforeEach(() => {
    get.mockReset()
    put.mockReset()
    get.mockImplementation(async (path: string) => {
      if (path.startsWith('/api/me/leaderboard')) return me
      if (path.startsWith('/api/leaderboard/podiums')) return { campaigns: [] }
      return board()
    })
  })

  it('頒獎台是 2-1-3，前三名用月桂葉環，匿名的人顯示「匿名的聖誕老人」', async () => {
    const { container } = renderPage()
    await screen.findByText('聖誕老人排行榜')

    const podium = container.querySelectorAll('.lb-podium .lb-pod')
    expect(podium).toHaveLength(3)
    // DOM 順序是第 2、1、3 名；版位由 lb-pod--N 決定
    expect(podium[0].className).toContain('lb-pod--2')
    expect(podium[1].className).toContain('lb-pod--1')
    expect(within(podium[1] as HTMLElement).getByText('送最多的人')).toBeTruthy()
    expect(within(podium[0] as HTMLElement).getByText('匿名的聖誕老人')).toBeTruthy()
    expect(container.querySelectorAll('.lb-podium svg[role="img"]')).toHaveLength(3)
  })

  it('名單先顯示 20 位，按「再看 20 位」逐批展開，全部顯示後按鈕消失', async () => {
    const { container } = renderPage()
    await screen.findByText('聖誕老人排行榜')

    expect(container.querySelectorAll('.lb-row')).toHaveLength(20)
    await userEvent.click(screen.getByRole('button', { name: /再看 20 位（還有 32 位）/ }))
    expect(container.querySelectorAll('.lb-row')).toHaveLength(40)
    await userEvent.click(screen.getByRole('button', { name: /再看 12 位（還有 12 位）/ }))
    expect(container.querySelectorAll('.lb-row')).toHaveLength(52)
    expect(screen.queryByRole('button', { name: /再看/ })).toBeNull()
  })

  it('自己那一列標「你」，我的成就顯示當期名次與累計稱號', async () => {
    const { container } = renderPage()
    await screen.findByText('我的成就')

    const mine = container.querySelector('.lb-row--me') as HTMLElement
    expect(within(mine).getByText('大樹')).toBeTruthy()       // 第 3 名、8 份、有公開名字
    expect(within(mine).getByText('你')).toBeTruthy()

    const card = screen.getByLabelText('我的成就')
    expect(within(card).getByText('第 3 名')).toBeTruthy()
    expect(within(card).getByText('再 2 份成為「傳奇聖誕老人」')).toBeTruthy()
  })

  it('切換「在排行榜顯示我的名字」會呼叫開關 API，並繞過瀏覽器快取重抓名單', async () => {
    renderPage()
    await screen.findByText('我的成就')

    await userEvent.click(screen.getByRole('switch', { name: '在排行榜顯示我的名字' }))

    expect(put).toHaveBeenCalledWith('/api/me/leaderboard-opt-in', { optIn: false })
    // 名單帶著 max-age=300，不強制回源的話會拿到還寫著匿名的那份
    await waitFor(() => {
      expect(get).toHaveBeenCalledWith('/api/leaderboard', { cache: 'reload' })
      expect(get).toHaveBeenCalledWith('/api/leaderboard?limit=3', { cache: 'reload' })
    })
  })

  it('直接開網址（沒有 intro state）不播進場動畫', async () => {
    const { container } = renderPage()
    await screen.findByText('聖誕老人排行榜')

    expect(container.querySelector('.lb-page')?.className).not.toContain('lb-page--intro')
  })

  it('從首頁「看排行榜」進來才播進場，聚光燈打在第 1 名', async () => {
    const { container } = renderPage({ intro: true })
    await screen.findByText('聖誕老人排行榜')

    expect(container.querySelector('.lb-page')?.className).toContain('lb-page--intro')
    expect(container.querySelector('.lb-pod--1 .lb-pod__spot')).toBeTruthy()
  })
})
