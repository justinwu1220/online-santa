import { FlagIcon, SleighIcon } from './icons'

/**
 * 送出禮物的進度條：白色雪橇往前、後面拖著雪花，終點放一面金色旗子（SPEC 第 7 節）。
 *
 * 首頁入口用 `sm`、排行榜頁用 `lg`，兩邊是同一個元件。尺寸用 px 寫死：這是一張圖，
 * 雪橇與旗子的位置是依軌道高度算出來的，跟著字級縮放反而會對不準。
 */
const SIZES = {
  sm: { height: 34, top: 22, bar: 6, sled: 30, flag: 20 },
  lg: { height: 48, top: 30, bar: 10, sled: 40, flag: 24 },
}

export function SledProgress({ delivered, goal, size = 'sm', className = '' }: {
  delivered: number
  goal: number
  size?: 'sm' | 'lg'
  className?: string
}) {
  const s = SIZES[size]
  const pct = `${goal > 0 ? Math.min(100, (delivered / goal) * 100) : 0}%`
  return (
    <div className={`sled-progress ${className}`.trim()} role="img"
      aria-label={`已送出 ${delivered} 份，目標 ${goal} 份`} style={{ height: s.height }}>
      <div className="sled-progress__track" style={{ top: s.top, height: s.bar }} />
      <div className="sled-progress__fill" style={{ top: s.top, height: s.bar, width: pct }} />
      <div className="sled-progress__trail" style={{ left: `calc(${pct} - 60px)`, top: s.top - 2, height: s.bar + 4 }} />
      {[0, 1, 2, 3].map((i) => (
        <span key={i} className="sled-progress__puff" style={{
          left: `calc(${pct} - ${14 + i * 9}px)`, top: s.top - 3 + (i % 2) * 3,
          width: 6 - i, height: 6 - i, animationDelay: `${i * 0.4}s`,
        }} />
      ))}
      <SleighIcon className="sled-progress__sled" width={s.sled} height={Math.round(s.sled * 0.85)}
        style={{ left: pct, top: s.top - Math.round(s.sled * 0.62), marginLeft: -Math.round(s.sled * 0.15) }} />
      {/* 旗杆在圖示的 x=5/24 處，往左挪這麼多才會剛好站在軌道 100% 的位置 */}
      <FlagIcon className="sled-progress__flag" width={s.flag} height={s.flag}
        style={{ left: `calc(100% - ${Math.round(s.flag * 5 / 24)}px)`, top: s.top + s.bar / 2 - s.flag + 2 }} />
    </div>
  )
}

/** 進度的文字（SPEC 第 7.1 節，寫法 F3）：一句話說完。 */
export function ProgressSentence({ delivered, goal, className = '' }: {
  delivered: number
  goal: number
  className?: string
}) {
  const left = goal - delivered
  if (left <= 0) {
    return <span className={className}>已送出 <strong>{delivered}</strong> 份，達成 {goal} 份目標！</span>
  }
  return (
    <span className={className}>
      已送出 <strong>{delivered}</strong> 份，再 <strong className="lb-num-mint">{left}</strong> 份就達成 {goal} 份
    </span>
  )
}
