import type { SVGProps } from 'react'

/**
 * 排行榜用的線條圖示（24×24、2px 圓角線），移植自交付的 React 參考實作。
 *
 * 沒有沿用 lucide-react：雪橇、終點旗與四個稱號圖示都是設計稿畫的，lucide 沒有對應的字形。
 */
const base = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
}

export function SleighIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" {...base} {...props}>
      <path d="M2 17.5h16.5a3.5 3.5 0 0 0 3.5-3.5" />
      <path d="M4 17.5V11h4l2 3.5h6.5L19 11" />
      <rect x="9" y="5" width="6" height="5" rx="1" />
      <path d="M12 5v5" />
    </svg>
  )
}

export function FlagIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" {...base} {...props}>
      <path d="M5 22V3" />
      <path d="M5 4h12l-3 4 3 4H5" fill="rgba(253,230,138,.28)" />
    </svg>
  )
}

export function ArrowRightIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" {...base} {...props}>
      <path d="M5 12h14" />
      <path d="m12 5 7 7-7 7" />
    </svg>
  )
}

export function ChevronLeftIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" {...base} {...props}>
      <path d="m15 18-6-6 6-6" />
    </svg>
  )
}

export function StarFill(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden {...props}>
      <path fill="currentColor" d="m12 2 2.4 7.2L22 12l-7.6 2.8L12 22l-2.4-7.2L2 12l7.6-2.8z" />
    </svg>
  )
}

/** 稱號圖示：傳奇聖誕老人＝聖誕帽、雪橇隊長＝雪橇、馴鹿＝愛心、小精靈＝星星。 */
const TIER_PATHS: Record<string, string> = {
  legend: 'M4.5 17.5C6.5 9.5 10 5 15 5c2.5 0 4 1.8 4 4.2V12M17 14.5a2 2 0 1 0 4 0a2 2 0 1 0-4 0'
    + 'M4.25 17.5h12.5a1.75 1.75 0 0 1 0 3.5H4.25a1.75 1.75 0 0 1 0-3.5Z',
  captain: 'M3 6h4l3 9h9M9 19a1 1 0 1 0 0-.01M18 19a1 1 0 1 0 0-.01M14 15l3-6h4',
  reindeer: 'M12 21s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 11c0 5.5-7 10-7 10Z',
  elf: 'M12 3l1.9 5.8L20 10l-6.1 1.2L12 17l-1.9-5.8L4 10l6.1-1.2z',
}

export function TierIcon({ modifier, ...props }: { modifier: string } & SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" {...base} {...props}>
      <path d={TIER_PATHS[modifier]} />
    </svg>
  )
}
