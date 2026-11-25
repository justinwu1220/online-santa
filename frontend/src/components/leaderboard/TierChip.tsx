import type { Tier } from '../../lib/tiers'

/** 稱號標籤（SPEC 第 4.3 節）。沒有稱號（一份都還沒送達）就不顯示。 */
export function TierChip({ tier, className = '' }: { tier: Tier | null; className?: string }) {
  if (!tier) return null
  return <span className={`tier-chip tier-chip--${tier.modifier} ${className}`.trim()}>{tier.name}</span>
}
