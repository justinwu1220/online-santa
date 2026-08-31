import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import type { PieLabelRenderProps } from 'recharts'

type Outcome = 'COMPLETED' | 'RELEASED' | 'CANCELLED'

// 驗證過的三色：綠／紅在色盲模擬下的區隔落在警戒帶（ΔE 6.8，dataviz 色彩公式的
// CVD separation 檢查），因此不能只靠顏色分辨——每一塊都帶文字圖例與直接標示的
// 筆數，色彩只是輔助不是唯一辨識管道。
const COLORS: Record<Outcome, string> = {
  COMPLETED: '#1f7a4c',
  RELEASED: '#c9860a',
  CANCELLED: '#c8384a',
}

const LABELS: Record<Outcome, string> = {
  COMPLETED: '完成',
  RELEASED: '釋回',
  CANCELLED: '取消',
}

const OUTCOMES: Outcome[] = ['COMPLETED', 'RELEASED', 'CANCELLED']

const RADIAN = Math.PI / 180

/**
 * 環圈中段的直接標示筆數。
 *
 * Recharts 的 `label` 若只回傳字串不會被畫出來——它預期收到 cx/cy/角度後自己算
 * 位置、回傳一個定位好的 `<text>`。白字：三個色塊（綠／黃／紅）都夠深，白字在上面
 * 都過對比（見 marks-and-anatomy 的「標籤在色塊內」例外）。
 */
function renderValueLabel({ cx, cy, midAngle, innerRadius, outerRadius, value }: PieLabelRenderProps) {
  const numericCx = Number(cx ?? 0)
  const numericCy = Number(cy ?? 0)
  const inner = Number(innerRadius ?? 0)
  const outer = Number(outerRadius ?? 0)
  const angle = midAngle ?? 0
  const radius = inner + (outer - inner) / 2
  const x = numericCx + radius * Math.cos(-angle * RADIAN)
  const y = numericCy + radius * Math.sin(-angle * RADIAN)
  return (
    <text x={x} y={y} fill="#ffffff" textAnchor="middle" dominantBaseline="central"
      fontSize={13} fontWeight={600}>
      {value}
    </text>
  )
}

/** 認領結果環圈圖：完成／釋回／取消。 */
export function ClaimOutcomeChart({ completed, released, cancelled }: {
  completed: number
  released: number
  cancelled: number
}) {
  const counts: Record<Outcome, number> = {
    COMPLETED: completed, RELEASED: released, CANCELLED: cancelled,
  }
  const total = completed + released + cancelled

  if (total === 0) {
    return <p className="py-16 text-center text-sm text-slate-400">這個年度還沒有已結束的認領</p>
  }

  const data = OUTCOMES
    .map((key) => ({ key, label: LABELS[key], value: counts[key] }))
    .filter((entry) => entry.value > 0)

  return (
    <ResponsiveContainer width="100%" height={240}>
      <PieChart>
        <Pie
          data={data}
          dataKey="value"
          nameKey="label"
          innerRadius={56}
          outerRadius={84}
          paddingAngle={2}
          strokeWidth={2}
          stroke="#ffffff"
          label={renderValueLabel}
          labelLine={false}
          isAnimationActive={false}
        >
          {data.map((entry) => <Cell key={entry.key} fill={COLORS[entry.key]} />)}
        </Pie>
        <Legend
          verticalAlign="bottom"
          formatter={(value: string) => <span className="text-sm text-slate-600">{value}</span>}
        />
        <Tooltip
          contentStyle={{ borderRadius: 8, borderColor: '#dbf0e3', fontSize: 13 }}
          formatter={(value) => [`${value} 筆`]}
        />
      </PieChart>
    </ResponsiveContainer>
  )
}
