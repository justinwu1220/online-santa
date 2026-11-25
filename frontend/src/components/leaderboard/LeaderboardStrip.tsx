import { useNavigate } from 'react-router-dom'
import { STRIP_LIMIT, useLeaderboard } from '../../lib/leaderboard'
import { LaurelMedal } from './LaurelMedal'
import { ProgressSentence, SledProgress } from './SledProgress'
import { ArrowRightIcon } from './icons'

/**
 * 首頁的排行榜入口（SPEC 第 2 節，版面 G1）：前三名｜標題＋進度｜看排行榜。
 *
 * - 前三名排成頒獎台：第 2 名在左、第 1 名在中間（獎牌較大）、第 3 名在右。
 * - <strong>整條不可點</strong>，只有「看排行榜 →」進得去；頁首選單也不放排行榜。
 * - 不顯示自己的份數或名次——那些只在排行榜頁的「我的成就」出現。
 * - 版面依入口<strong>自己的寬度</strong>切三種（container query，見 rd-rank.css），
 *   不靠自由換行。
 *
 * 只抓前三名（limit=3）：首頁每開一次就會打一次 API，不必為了三顆膠囊搬整份名單。
 * 還沒有人上榜（或檔期還沒建立）時整條不顯示，首頁維持原樣。
 */
export function LeaderboardStrip() {
  const navigate = useNavigate()
  const board = useLeaderboard(STRIP_LIMIT)

  if (!board.data || board.data.entries.length === 0) return null
  const { entries, delivered, goal } = board.data
  const podium = [entries[1], entries[0], entries[2]].filter(Boolean)

  return (
    <div className="lb lb-wrap">
      <section className="lb-strip" aria-labelledby="lbStripTitle">
        <ol className="lb-top" aria-label="排行榜前三名">
          {podium.map((entry) => (
            <li key={`${entry.rank}-${entry.name}-${entry.delivered}`} className="lb-pill">
              <LaurelMedal rank={entry.rank} size={entry.rank === 1 ? 46 : 40} className="lb-pill__medal" />
              <span className="lb-pill__name">{entry.name}</span>
              <span className="lb-pill__count">{entry.delivered} 份</span>
            </li>
          ))}
        </ol>

        <div className="lb-mid">
          <div className="lb-head">
            <h2 id="lbStripTitle" className="lb-head__title">聖誕老人排行榜</h2>
            <ProgressSentence className="lb-head__sentence" delivered={delivered} goal={goal} />
          </div>
          <SledProgress className="lb-mid__bar" delivered={delivered} goal={goal} size="sm" />
        </div>

        <div className="lb-cta">
          {/* 用按鈕而不是連結：進場動畫要靠導覽帶的 state，直接開網址或重新整理就不播 */}
          <button type="button" className="lb-cta__link"
            onClick={() => navigate('/leaderboard', { state: { intro: true } })}>
            看排行榜<ArrowRightIcon className="lb-cta__arrow" width={18} height={18} />
          </button>
        </div>
      </section>
    </div>
  )
}
