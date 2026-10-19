import { useEffect, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { DIcon, type DrawerIcon } from '../../components/console/DrawerParts'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { pageTitle } from '../../lib/brand'
import { formatDateTime } from '../../lib/format'
import type { PlatformStats } from '../../lib/types'
import { usePlatformStats } from './adminQueries'

/**
 * 管理員總覽（設計稿 16.1）：先看「需要你處理」，再看活動進度，最後看細項。
 * 數字都來自 `/api/admin/stats`；每一張卡點了都會帶著篩選跳到對應的清單。
 */
export function AdminOverview() {
  const stats = usePlatformStats()

  useEffect(() => {
    document.title = pageTitle('總覽｜管理員後台')
    return () => { document.title = pageTitle() }
  }, [])

  return (
    <section className="org-tab" aria-labelledby="adOverTitle">
      <div className="org-head">
        <h1 id="adOverTitle">總覽</h1>
        {stats.data && <span className="ov-time">統計於 {formatDateTime(stats.data.generatedAt)}</span>}
      </div>
      {stats.isLoading && <Spinner label="載入統計" />}
      {stats.isError && <ErrorNotice error={stats.error} onRetry={() => void stats.refetch()} />}
      {stats.data && <Overview stats={stats.data} />}
    </section>
  )
}

function Overview({ stats }: { stats: PlatformStats }) {
  const navigate = useNavigate()
  const go = (tab: 'orgs' | 'wishes', filter: string) => navigate(`/admin/${tab}?filter=${filter}`)

  const org = stats.organizations
  const wish = stats.wishes
  const claim = stats.claims
  const pendingOrgs = org.PENDING ?? 0
  const review = wish.PENDING_REVIEW ?? 0
  const open = wish.AVAILABLE ?? 0
  const claimed = wish.CLAIMED ?? 0
  const done = wish.FULFILLED ?? 0
  const late = stats.overdueClaims
  const frozen = stats.frozenWishes
  const approved = org.APPROVED ?? 0
  const wishTotal = review + open + claimed + done
  const orgTotal = Object.values(org).reduce((a, b) => a + b, 0)

  const todo: { n: number; label: string; desc: string; tab: 'orgs' | 'wishes'; filter: string; tone?: string }[] = [
    { n: pendingOrgs, label: '機構待審核', desc: '核准後機構才能刊登願望', tab: 'orgs', filter: 'pending' },
    { n: review, label: '願望待審核', desc: '核准後才會出現在願望牆', tab: 'wishes', filter: 'review' },
    { n: late, label: '認領逾期未寄送', desc: '孩子的願望正被卡著', tab: 'wishes', filter: 'late', tone: 'late' },
    { n: frozen, label: '已凍結的願望', desc: '確認處理完再解除凍結', tab: 'wishes', filter: 'frozen', tone: 'frozen' },
  ]

  const users = [
    { label: '認領者', n: stats.users.DONOR ?? 0 },
    { label: '機構成員', n: stats.users.ORG_MEMBER ?? 0 },
    { label: '管理員', n: stats.users.ADMIN ?? 0 },
  ]
  const userTotal = users.reduce((a, r) => a + r.n, 0)

  return (
    <>
      <section aria-labelledby="ovTodoTitle" className="ov-block">
        <h2 className="ov-title" id="ovTodoTitle">需要你處理</h2>
        <div className="ov-todo">
          {todo.map((item) => (
            <button key={item.filter} type="button" className={`todo ${item.n ? 'is-hot' : 'is-zero'}`}
              data-tone={item.tone ?? ''} onClick={() => go(item.tab, item.filter)}>
              <span className="todo__num">{item.n}</span>
              <span className="todo__label">{item.label}</span>
              <span className="todo__desc">{item.desc}</span>
              <span className="todo__go">{item.n ? '前往處理 →' : '目前沒有'}</span>
            </button>
          ))}
        </div>
      </section>

      <section aria-labelledby="ovProgTitle" className="panel ov-progress">
        <div className="ov-progress__head">
          <h2 className="ov-title" id="ovProgTitle">活動進度</h2>
          <span className="ov-progress__goal">目標 {stats.goalWishes} 份禮物・{stats.goalOrganizations} 間機構</span>
        </div>
        <div className="ov-goals">
          <Goal icon="gift" label="已送到孩子手上" goal={stats.goalWishes} unit="份禮物"
            segs={[['已送到孩子手上', done], ['認領中', claimed], ['可認領', open]]} />
          <Goal icon="building" label="已核准的合作機構" goal={stats.goalOrganizations} unit="間機構"
            segs={[['已核准', approved], ['待審核', pendingOrgs]]} />
        </div>
      </section>

      <section aria-labelledby="ovDetailTitle" className="ov-block">
        <h2 className="ov-title" id="ovDetailTitle">細項</h2>
        <div className="ov-grid">
          <section className="ov-card">
            <CardHead icon="building" title="機構" n={orgTotal} unit="間" />
            <Rows total={orgTotal} rows={[
              { label: '已核准', n: approved },
              { label: '待審核', n: pendingOrgs, go: () => go('orgs', 'pending') },
              { label: '已退件', n: org.REJECTED ?? 0, go: () => go('orgs', 'rejected'), tone: 'late' },
              { label: '已停權', n: org.SUSPENDED ?? 0, go: () => go('orgs', 'suspended'), tone: 'frozen' },
            ]} />
          </section>

          <section className="ov-card ov-card--wide">
            <CardHead icon="gift" title="願望" n={wishTotal} unit="則" />
            <ol className="ov-flow">
              {([['待審核', review, 'review'], ['可認領', open, 'open'], ['已認領', claimed, 'claimed'], ['已完成', done, 'done']] as const)
                .map(([label, n, filter]) => (
                  <li key={filter}>
                    <button type="button" className={`ov-step ${filter === 'review' && n ? 'is-flag' : ''} ${n ? '' : 'is-zero'}`}
                      onClick={() => go('wishes', filter)}>
                      <strong>{n}</strong><span>{label}</span>
                    </button>
                  </li>
                ))}
            </ol>
            <div className="ov-flags">
              <button type="button" className={`ov-flag ${late ? 'is-hot' : ''} ${late ? '' : 'is-zero'}`}
                data-tone="late" onClick={() => go('wishes', 'late')}>逾期未寄<strong>{late}</strong></button>
              <button type="button" className={`ov-flag ${frozen ? '' : 'is-zero'}`}
                data-tone="frozen" onClick={() => go('wishes', 'frozen')}>已凍結<strong>{frozen}</strong></button>
              <span className={`ov-flag ${claim.CANCELLED ? '' : 'is-zero'}`}>認領後取消<strong>{claim.CANCELLED ?? 0}</strong></span>
            </div>
          </section>

          <section className="ov-card">
            <CardHead icon="user" title="使用者" n={userTotal} unit="人" />
            <Rows total={userTotal} rows={users} />
          </section>
        </div>
      </section>
    </>
  )
}

function CardHead({ icon, title, n, unit }: { icon: DrawerIcon; title: string; n: number; unit: string }) {
  return (
    <div className="ov-card__head">
      <h3><DIcon name={icon} />{title}</h3>
      <span><strong>{n}</strong> {unit}</span>
    </div>
  )
}

function Rows({ rows, total }: {
  total: number
  rows: { label: string; n: number; go?: () => void; tone?: string }[]
}) {
  return (
    <ul className="ov-rows">
      {rows.map(({ label, n, go, tone }) => {
        const p = total ? (n / total) * 100 : 0
        const inner: ReactNode = (
          <>
            <span className="ov-row__label">{label}</span>
            <span className="ov-row__track"><i style={{ width: `${p}%` }} /></span>
            <span className="ov-row__val">{n}<small>{p.toFixed(0)}%</small></span>
          </>
        )
        return (
          <li key={label} className={`ov-row ${n ? '' : 'is-zero'} ${go && n ? 'is-flag' : ''}`}
            data-tone={tone ?? ''} title={`${label}：${n}（${p.toFixed(0)}%）`}>
            {go && n ? <button type="button" className="ov-row__go" onClick={go}>{inner}</button> : inner}
          </li>
        )
      })}
    </ul>
  )
}

/** 活動進度：同一個綠色由深到淺＝已達成 → 進行中 → 還在等待。 */
function Goal({ icon, label, goal, unit, segs }: {
  icon: DrawerIcon
  label: string
  goal: number
  unit: string
  segs: [string, number][]
}) {
  const n = segs[0][1]
  const pct = goal ? (n / goal) * 100 : 0
  const summary = segs.map(([l, v]) => `${l} ${v}`)
  return (
    <div className="goal">
      <div className="goal__label"><DIcon name={icon} />{label}</div>
      <div className="goal__num"><strong>{n}</strong><span>/ {goal} {unit}</span><em>{pct.toFixed(1).replace(/\.0$/, '')}%</em></div>
      <div className="goal__bar" role="img" aria-label={`${summary.join('，')}，目標 ${goal}`}
        title={`${summary.join('・')}・目標 ${goal}`}>
        {segs.map(([l, v], i) => v
          ? <i key={l} className={`seg-${i}`} style={{ width: `${Math.max((v / goal) * 100, 1.2)}%` }} />
          : null)}
      </div>
      <p className="goal__note">
        {segs.map(([l, v], i) => (
          <span key={l} className="goal__item"><span className={`goal__key seg-${i}`} />{l} {v}</span>
        ))}
      </p>
    </div>
  )
}
