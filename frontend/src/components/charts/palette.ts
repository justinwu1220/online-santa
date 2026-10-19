export type ChartTheme = 'light' | 'dark'

/** 圖表配色。後台改版期間機構頁已是深色、管理員頁還是亮色，所以兩組並存。 */
export const CHART_PALETTE = {
  light: {
    grid: '#e1e0d9', axis: '#c3c2b7', tick: '#898781', cursor: '#f0f9f4',
    tooltipBorder: '#dbf0e3', tooltipBg: '#ffffff', tooltipText: '#0f172a',
    bar: '#1f7a4c', sliceStroke: '#ffffff', legend: '#475569',
    completed: '#1f7a4c', released: '#c9860a', cancelled: '#c8384a',
  },
  dark: {
    grid: 'rgba(255,255,255,.08)', axis: 'rgba(255,255,255,.2)', tick: '#94a3b8', cursor: 'rgba(255,255,255,.06)',
    tooltipBorder: 'rgba(255,255,255,.18)', tooltipBg: '#0f172a', tooltipText: '#f8fafc',
    bar: '#6ee7b7', sliceStroke: '#0f172a', legend: '#cbd5e1',
    completed: '#6ee7b7', released: '#fcd34d', cancelled: '#fca5a5',
  },
} as const

export function tooltipStyle(theme: ChartTheme) {
  const p = CHART_PALETTE[theme]
  return { borderRadius: 8, borderColor: p.tooltipBorder, background: p.tooltipBg, color: p.tooltipText, fontSize: 13 }
}
