import { describe, expect, it } from 'vitest'
import { TIERS_ASCENDING, nextTierOf, tierByKey, tierOf } from './tiers'

/** 門檻與後端的 com.onlinesanta.leaderboard.Tier 必須一致：1／3／5／10 份。 */
describe('稱號', () => {
  it('看累計份數解鎖，門檻之間維持前一級', () => {
    expect(tierOf(0)).toBeNull()
    expect(tierOf(1)?.name).toBe('小精靈')
    expect(tierOf(2)?.name).toBe('小精靈')
    expect(tierOf(3)?.name).toBe('馴鹿')
    expect(tierOf(5)?.name).toBe('雪橇隊長')
    expect(tierOf(9)?.name).toBe('雪橇隊長')
    expect(tierOf(10)?.name).toBe('傳奇聖誕老人')
    expect(tierOf(99)?.name).toBe('傳奇聖誕老人')
  })

  it('下一個稱號是還沒達到的最低一級，最高級沒有下一個', () => {
    expect(nextTierOf(0)?.name).toBe('小精靈')
    expect(nextTierOf(1)?.name).toBe('馴鹿')
    expect(nextTierOf(4)?.name).toBe('雪橇隊長')
    expect(nextTierOf(5)?.name).toBe('傳奇聖誕老人')
    expect(nextTierOf(10)).toBeNull()
  })

  it('用 API 回傳的 key 取得稱號；沒有稱號時是 null', () => {
    expect(tierByKey('REINDEER')?.name).toBe('馴鹿')
    expect(tierByKey(null)).toBeNull()
    expect(tierByKey(undefined)).toBeNull()
  })

  it('說明用的順序是由低到高', () => {
    expect(TIERS_ASCENDING.map((tier) => tier.min)).toEqual([1, 3, 5, 10])
  })
})
