import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { LaurelMedal } from '../components/leaderboard/LaurelMedal'
import { ProgressSentence, SledProgress } from '../components/leaderboard/SledProgress'
import { TierChip } from '../components/leaderboard/TierChip'
import { ChevronLeftIcon, StarFill, TierIcon } from '../components/leaderboard/icons'
import { EmptyBox, ErrorNotice, Spinner } from '../components/ui/Spinner'
import { useAuth } from '../lib/authContext'
import { pageTitle } from '../lib/brand'
import { useLeaderboard, useMyAchievement, usePodiums, useSetLeaderboardName } from '../lib/leaderboard'
import { reducedMotion } from '../lib/motion'
import { TIERS_ASCENDING, tierByKey } from '../lib/tiers'
import type { LeaderboardEntry, MyAchievement } from '../lib/types'

/** 名單一次顯示幾位，按「再看 N 位」再多一批（SPEC 第 3.5 節）。 */
const PAGE_SIZE = 20

/**
 * 聖誕老人排行榜（SPEC 第 3 節）。
 *
 * 進場動畫（E6 頒獎台升起）只有從首頁「看排行榜」進來才播：那顆按鈕導覽時帶 `state.intro`，
 * 直接開網址、重新整理、按返回都沒有這個 state，所以不播。開了「減少動態效果」也不播。
 */
export function Leaderboard() {
  const location = useLocation()
  const { email } = useAuth()
  const board = useLeaderboard()
  const mine = useMyAchievement(Boolean(email))
  const [shown, setShown] = useState(PAGE_SIZE)
  // 只在掛載時決定：之後 setState 重繪不該讓動畫重播
  const [intro] = useState(() => (location.state as { intro?: boolean } | null)?.intro === true && !reducedMotion())

  useEffect(() => {
    document.title = pageTitle('聖誕老人排行榜')
    return () => { document.title = pageTitle() }
  }, [])

  if (board.isLoading) return <Spinner label="載入排行榜" />
  if (board.isError) return <ErrorNotice error={board.error} onRetry={() => void board.refetch()} />
  if (!board.data) return null

  const { entries, delivered, goal, donorCount } = board.data
  const podium = entries.slice(0, 3)
  const order = [podium[1], podium[0], podium[2]].filter(Boolean)
  const visible = entries.slice(0, shown)
  const remaining = entries.length - visible.length

  return (
    <main className={`lb lb-page${intro ? ' lb-page--intro' : ''}`}>
      <div className="lb-intro-rise lb-intro-rise--1 lb-page__head">
        <Link className="lb-back" to="/"><ChevronLeftIcon width={16} height={16} />回願望牆</Link>
        <h1 className="lb-page__title">聖誕老人排行榜</h1>
        <p className="lb-page__lede">
          每一份送到孩子手上的禮物都算數。排名依「機構確認收到」的禮物份數，認領後還沒送達的不計入。
        </p>
      </div>

      <section className="lb-card lb-goal lb-intro-rise lb-intro-rise--2" aria-labelledby="lbGoalTitle">
        <h2 id="lbGoalTitle" className="lb-goal__title">大家一起送出的禮物</h2>
        <SledProgress className="lb-goal__bar" delivered={delivered} goal={goal} size="lg" />
        <p className="lb-goal__sentence"><ProgressSentence delivered={delivered} goal={goal} /></p>
      </section>

      {entries.length === 0 ? (
        <EmptyBox title="還沒有人上榜" hint="機構確認收到第一份禮物之後，排行榜就會出現。" />
      ) : (
        <>
          <ol className="lb-podium" aria-label="前三名">
            {order.map((entry) => {
              const place = podium.indexOf(entry) + 1      // 1、2、3：決定版位與升起順序
              return (
                <li key={`${entry.rank}-${entry.name}-${entry.delivered}`}
                  className={`lb-pod lb-pod--${place} lb-pod--medal-${Math.min(entry.rank, 3)}`}>
                  {place === 1 && intro && (
                    <>
                      <span className="lb-pod__spot" aria-hidden />
                      <StarFill className="lb-pod__star" width={22} height={22} />
                    </>
                  )}
                  <LaurelMedal rank={entry.rank} size={place === 1 ? 76 : 60} className="lb-pod__medal" />
                  <span className="lb-pod__name">{entry.name}</span>
                  <span className="lb-pod__count">
                    <span className="lb-pod__num">{entry.delivered}</span><span className="lb-pod__unit">份</span>
                  </span>
                  <TierChip tier={tierByKey(entry.tier)} className="lb-pod__tier" />
                </li>
              )
            })}
          </ol>

          <div className="lb-columns lb-intro-rise lb-intro-rise--3">
            <section className="lb-card lb-list" aria-labelledby="lbListTitle">
              <h2 id="lbListTitle" className="lb-list__title">
                全部排名<span className="lb-list__total">共 {donorCount} 位聖誕老人</span>
              </h2>
              <ol className="lb-list__rows">
                {visible.map((entry, index) => (
                  <Row key={`${entry.rank}-${entry.name}-${index}`} entry={entry} me={mine.data} />
                ))}
              </ol>
              {remaining > 0 && (
                <div className="lb-list__more">
                  <button type="button" className="lb-more" onClick={() => setShown((n) => n + PAGE_SIZE)}>
                    再看 {Math.min(PAGE_SIZE, remaining)} 位（還有 {remaining} 位）
                  </button>
                </div>
              )}
            </section>

            <aside className="lb-aside">
              {mine.data && <MyAchievementCard me={mine.data} />}
              <TierGuide />
              <PastPodiums />
            </aside>
          </div>
        </>
      )}
    </main>
  )
}

/**
 * 名單的一列。
 *
 * 「這一列是我」是前端比對出來的：名單對所有人回同一份才快取得住，所以後端沒有標。
 * 用名次＋份數＋名字比對；並列又都匿名時可能標到同名次的另一列，但兩列顯示的內容一模一樣。
 */
function Row({ entry, me }: { entry: LeaderboardEntry; me?: MyAchievement }) {
  const isMe = Boolean(me?.ranked && me.rank === entry.rank && me.delivered === entry.delivered
    && (me.showName ? !entry.anonymous : entry.anonymous))
  return (
    <li className={`lb-row${isMe ? ' lb-row--me' : ''}`}>
      <span className="lb-row__rank">
        {entry.rank <= 3 ? <LaurelMedal rank={entry.rank} size={34} /> : entry.rank}
      </span>
      <span className="lb-row__who">
        <span className="lb-row__name">{entry.name}</span>
        {isMe && <span className="lb-you">你</span>}
        <TierChip tier={tierByKey(entry.tier)} />
      </span>
      <span className="lb-row__count"><strong>{entry.delivered}</strong> 份</span>
    </li>
  )
}

/** 我的成就（版面 H1「三行就好」）：名次與份數是當期的，稱號是累計解鎖的。 */
function MyAchievementCard({ me }: { me: MyAchievement }) {
  const save = useSetLeaderboardName()
  const tier = tierByKey(me.tier)
  const next = tierByKey(me.nextTier)
  const progress = next ? Math.min(100, (me.lifetimeDelivered / next.min) * 100) : 100

  return (
    <section className="lb-card lb-me" aria-labelledby="lbMeTitle">
      <div className="lb-me__head">
        <h2 id="lbMeTitle" className="lb-card__title">我的成就</h2>
        <span className="lb-me__rank">{me.ranked ? `第 ${me.rank} 名` : '還沒上榜'}</span>
      </div>
      <div className="lb-me__count">
        <strong>{me.delivered}</strong><span>份</span><TierChip tier={tier} />
      </div>
      <div className="lb-me__next">
        <div className="lb-bar" role="img"
          aria-label={next ? `距離下一個稱號：累計 ${me.lifetimeDelivered} / ${next.min} 份` : '已是最高稱號'}>
          <i style={{ width: `${progress}%` }} />
        </div>
        <span>{next ? `再 ${me.toNextTier} 份成為「${next.name}」` : '已是最高稱號'}</span>
      </div>
      <div className="lb-me__switch">
        <label htmlFor="lbShowName">在排行榜顯示我的名字</label>
        <span className="lb-switch">
          <input id="lbShowName" type="checkbox" role="switch" checked={me.showName} disabled={save.isPending}
            onChange={(event) => save.mutate(event.target.checked)} />
          <i />
        </span>
      </div>
    </section>
  )
}

/** 稱號怎麼來：四級的圖示、名稱、說明與門檻。 */
function TierGuide() {
  return (
    <section className="lb-card lb-tiers" aria-labelledby="lbTierTitle">
      <h2 id="lbTierTitle" className="lb-card__title">稱號怎麼來</h2>
      <ul className="lb-tiers__list">
        {TIERS_ASCENDING.map((tier) => (
          <li key={tier.key} className={`lb-tier lb-tier--${tier.modifier}`}>
            <span className="lb-tier__icon"><TierIcon modifier={tier.modifier} width={18} height={18} /></span>
            <span className="lb-tier__text">
              <span className="lb-tier__name">{tier.name}</span>
              <span className="lb-tier__desc">{tier.desc}</span>
            </span>
            <span className="lb-tier__need">{tier.min} 份起</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

/** 歷屆前三名。每屆結案時記下來，之後不會再變。還沒有結案過的檔期就不顯示這張卡。 */
function PastPodiums() {
  const podiums = usePodiums()
  const campaigns = podiums.data?.campaigns ?? []
  if (campaigns.length === 0) return null

  return (
    <section className="lb-card lb-past" aria-labelledby="lbPastTitle">
      <h2 id="lbPastTitle" className="lb-card__title">歷屆前三名</h2>
      {campaigns.map((campaign) => (
        <div key={campaign.campaignName} className="lb-past__campaign">
          <span className="lb-past__name">{campaign.campaignName}</span>
          <ol className="lb-past__rows">
            {campaign.entries.map((entry, index) => (
              <li key={`${entry.rank}-${index}`} className="lb-past__row">
                <LaurelMedal rank={entry.rank} size={26} className="lb-past__rank" />
                <span>{entry.name}</span>
                <span className="lb-past__count">{entry.delivered} 份</span>
              </li>
            ))}
          </ol>
        </div>
      ))}
    </section>
  )
}
