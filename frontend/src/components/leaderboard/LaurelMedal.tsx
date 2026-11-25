/**
 * 名次：月桂冠（SPEC 第 4.2 節）。
 *
 * 兩束月桂葉圍成一圈，中間是名次色的細環，<strong>環心寫名次數字</strong>——名單裡第 1～3 名
 * 用這個圖示取代數字，數字必須看得見。並列時用同一個名次的顏色與數字（兩個第 2 名都是銀色 2）。
 * 第 4 名以後不會用到這個圖示（名單直接寫數字），超過 3 的 rank 一律當銅色處理。
 */

/** 金／銀／銅。環線與數字同色，底色是網站的深色背景。 */
const COLORS: Record<number, string> = { 1: '#facc15', 2: '#cbd5e1', 3: '#f59e0b' }

/**
 * 葉片沿著半徑 16 的圓排開，左右各六片，長軸順著枝條並向外傾 26 度（算好的，別手改）：
 * [cx, cy, 旋轉角度, rx, ry]。外側兩片小一點，看起來才像自然收尾。
 */
const LEAVES: [number, number, number, number, number][] = [
  [18.5, 39, 84, 2, 3.6], [12.5, 35.1, 110, 2.3, 4.2], [8.8, 28.9, 136, 2.4, 4.6],
  [8.2, 21.8, 162, 2.4, 4.6], [10.7, 15.1, -172, 2.3, 4.2], [16, 10.1, -146, 2, 3.6],
  [29.5, 39, 96, 2, 3.6], [35.5, 35.1, 70, 2.3, 4.2], [39.2, 28.9, 44, 2.4, 4.6],
  [39.8, 21.8, 18, 2.4, 4.6], [37.3, 15.1, -8, 2.3, 4.2], [32, 10.1, -34, 2, 3.6],
]

/** 深淺兩種綠交錯，平面的葉子才有層次。 */
const LEAF_DARK = '#15803d'
const LEAF_LIGHT = '#22c55e'

export function LaurelMedal({ rank, size = 40, className }: { rank: number; size?: number; className?: string }) {
  const color = COLORS[Math.min(Math.max(rank, 1), 3)]

  return (
    <svg role="img" aria-label={`第 ${rank} 名`} width={size} height={size} viewBox="0 0 48 48"
      className={className}>
      {/* 枝條：兩段圓弧，底部用一個小結收起來 */}
      <g fill="none" stroke={LEAF_DARK} strokeWidth="1.5" strokeLinecap="round">
        <path d="M20 36.2A12.8 12.8 0 0 1 18 12.7" />
        <path d="M28 36.2A12.8 12.8 0 0 0 30 12.7" />
        <path d="M20 36.2c1.6 4.2 6.4 4.2 8 0" />
      </g>

      {LEAVES.map(([cx, cy, deg, rx, ry], index) => (
        <ellipse key={`${cx}-${cy}`} cx={cx} cy={cy} rx={rx} ry={ry}
          transform={`rotate(${deg} ${cx} ${cy})`}
          fill={index % 2 === 0 ? LEAF_DARK : LEAF_LIGHT} />
      ))}

      <circle cx="24" cy="24" r="10.6" fill="rgba(2, 6, 23, .55)" stroke={color} strokeWidth="2.4" />
      <text x="24" y="24.4" textAnchor="middle" dominantBaseline="central" fontSize="15" fontWeight="800"
        fill={color}>
        {Math.min(Math.max(rank, 1), 3)}
      </text>
    </svg>
  )
}
