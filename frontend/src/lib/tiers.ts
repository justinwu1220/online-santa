/**
 * 排行榜稱號（SPEC 第 4.3 節）。
 *
 * 稱號看的是<strong>累計</strong>送達份數，解鎖之後永久持有，不隨檔期重置——名次才是當期的。
 * 後端的 `com.onlinesanta.leaderboard.Tier` 是同一組門檻，API 回傳的是 key（ELF…），
 * 兩邊的 key 必須一致；中文名稱與顏色只在前端這一份。
 */
export type TierKey = 'ELF' | 'REINDEER' | 'CAPTAIN' | 'LEGEND'

export interface Tier {
  key: TierKey
  /** CSS 的修飾字（tier-chip--elf…） */
  modifier: string
  name: string
  desc: string
  min: number
}

/** 由高到低，`tierOf` 由上往下找第一個達標的。 */
export const TIERS: Tier[] = [
  { key: 'LEGEND', modifier: 'legend', name: '傳奇聖誕老人', desc: '把 10 份禮物送到孩子手上', min: 10 },
  { key: 'CAPTAIN', modifier: 'captain', name: '雪橇隊長', desc: '帶著禮物跑遍好幾個機構', min: 5 },
  { key: 'REINDEER', modifier: 'reindeer', name: '馴鹿', desc: '持續送出溫暖的好夥伴', min: 3 },
  { key: 'ELF', modifier: 'elf', name: '小精靈', desc: '第一份禮物送到了', min: 1 },
]

/** 稱號說明用：由低到高列出四級。 */
export const TIERS_ASCENDING = [...TIERS].reverse()

export const ANONYMOUS_NAME = '匿名的聖誕老人'

export function tierByKey(key: TierKey | null | undefined): Tier | null {
  return key ? TIERS.find((tier) => tier.key === key) ?? null : null
}

export function tierOf(delivered: number): Tier | null {
  return TIERS.find((tier) => delivered >= tier.min) ?? null
}

export function nextTierOf(delivered: number): Tier | null {
  return TIERS_ASCENDING.find((tier) => tier.min > delivered) ?? null
}
