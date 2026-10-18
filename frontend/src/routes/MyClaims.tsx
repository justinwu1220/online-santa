import { useQuery } from '@tanstack/react-query'
import { Gift } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, withQuery } from '../lib/api'
import { avatarFor } from '../lib/avatar'
import { useAuth } from '../lib/authContext'
import { daysUntil, formatDate } from '../lib/format'
import type { ClaimDonorView, DonorAnnualSummary, PageResponse } from '../lib/types'
import { claimTabOf } from '../lib/wishStatus'
import { Pager } from '../components/ui/Pager'
import { EmptyBox, ErrorNotice, Spinner } from '../components/ui/Spinner'

export function MyClaims() {
  const { email } = useAuth()
  const [year, setYear] = useState<number | ''>('')
  const [page, setPage] = useState(0)

  const claims = useQuery({
    queryKey: ['claims', 'mine', year, page],
    queryFn: () => api.get<PageResponse<ClaimDonorView>>(
      withQuery('/api/claims/me', { year: year === '' ? undefined : year, page, size: 10 })),
    enabled: Boolean(email),
  })

  // 一併取年度小結——即使目前選的是「全部」也要打，年份下拉的選項就是靠它的
  // availableYears，不必另開一支端點
  const summary = useQuery({
    queryKey: ['claims', 'annual-summary', year],
    queryFn: () => api.get<DonorAnnualSummary>(
      withQuery('/api/claims/me/annual-summary', { year: year === '' ? undefined : year })),
    enabled: Boolean(email),
  })

  if (!email) {
    return <div className="notice notice--info" style={{ marginTop: 40 }}>請先在右上角登入，才能查看自己的認領。</div>
  }

  const availableYears = summary.data?.availableYears ?? []

  return (
    <section>
      <div className="list-head">
        <h1>我的認領</h1>
        <p>寄出禮物後記得回來回報，機構才知道要準備收件。</p>
      </div>

      <div className="toolbar">
        <select className="input" aria-label="年度" value={year}
          onChange={(event) => {
            setYear(event.target.value === '' ? '' : Number(event.target.value))
            setPage(0)
          }}>
          <option value="">全部年度</option>
          {availableYears.map((y) => <option key={y} value={y}>{y}</option>)}
        </select>
      </div>

      {/* 只在挑了特定年度時出現——「全部」跨年度加總沒有 cohort 的意義，數字容易被誤讀 */}
      {year !== '' && summary.data && (
        <dl className="stat-row">
          <Stat value={summary.data.claimedCount} label="認領" />
          <Stat value={summary.data.completedCount} label="完成" />
          <Stat value={summary.data.childrenHelped} label="送禮孩子" unit="位" />
          <Stat value={summary.data.organizationsSupported} label="支持機構" unit="間" />
        </dl>
      )}

      {claims.isLoading && <Spinner label="載入認領" />}
      {claims.isError && <ErrorNotice error={claims.error} onRetry={() => void claims.refetch()} />}

      {claims.data?.content.length === 0 && (
        <EmptyBox title="還沒有認領任何願望">
          <Link to="/" className="btn">去願望牆看看</Link>
        </EmptyBox>
      )}

      {claims.data && claims.data.content.length > 0 && (
        <div className="wish-grid">
          {claims.data.content.map((claim) => <ClaimCard key={claim.id} claim={claim} />)}
        </div>
      )}

      {claims.data && <Pager page={claims.data} onChange={setPage} />}
    </section>
  )
}

function Stat({ value, label, unit }: { value: number; label: string; unit?: string }) {
  return (
    <div className="stat">
      <dt>{label}</dt>
      <dd>
        {value}
        {unit && <span style={{ marginLeft: 4, fontSize: 15, fontWeight: 500, color: 'var(--fg-4)' }}>{unit}</span>}
      </dd>
    </div>
  )
}

/** 同願望卡，標籤顯示「自己的進度」而不是願望的狀態（SPEC 11.2）。 */
function ClaimCard({ claim }: { claim: ClaimDonorView }) {
  const tab = claimTabOf(claim.status)
  const remaining = daysUntil(claim.shipDeadlineAt)
  const awaitingShipment = claim.status === 'CLAIMED'

  return (
    <Link
      to={`/me/claims/${claim.id}`}
      className="wish-card"
      data-status={tab.key}
      aria-label={`${claim.childAlias}，${claim.ageRangeLabel}，${claim.organizationName}，${claim.wishTitle}，${tab.label}`}
    >
      <span className="status-tab">{tab.label}</span>

      <div className="who">
        <img className="avatar" src={avatarFor(claim.wishId, claim.ageRange)} alt="" width={64} height={64} />
        <div className="who__text">
          <div className="who__name-row">
            <span className="who__name">{claim.childAlias}</span>
            <span className="who__age">{claim.ageRangeLabel}</span>
          </div>
          <div className="who__org">{claim.organizationName}</div>
        </div>
      </div>

      <div className="gift-wrap">
        <span className="gift">
          <Gift className="icon" aria-hidden />
          <span className="gift__text">{claim.wishTitle}</span>
        </span>
        <p style={{ margin: '12px 0 0', fontSize: 13, lineHeight: 1.7, color: claim.overdue ? '#fca5a5' : 'var(--fg-4)' }}>
          {awaitingShipment && remaining !== null
            ? remaining >= 0
              ? `請於 ${formatDate(claim.shipDeadlineAt)} 前寄出（還有 ${remaining} 天）`
              : `已逾期 ${Math.abs(remaining)} 天，機構可能會收回這個願望`
            : `認領於 ${formatDate(claim.claimedAt)}`}
          {claim.unreadMessageCount > 0 && (
            <span className="nav-count" style={{ marginLeft: 8 }}>{claim.unreadMessageCount} 則新訊息</span>
          )}
        </p>
      </div>
    </Link>
  )
}
