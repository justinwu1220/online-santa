import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, withQuery } from '../lib/api'
import { useAuth } from '../lib/authContext'
import { CLAIM_STATUS_LABELS, daysUntil, formatDate } from '../lib/format'
import type { ClaimDonorView, DonorAnnualSummary, PageResponse } from '../lib/types'
import { EmptyState, ErrorBanner, Notice, Spinner } from '../components/Feedback'
import { Select } from '../components/Form'
import { Pagination } from '../components/Pagination'
import { ClaimStatusBadge, OverdueBadge } from '../components/StatusBadge'

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
    return <Notice>請先在右上角登入，才能查看自己的認領。</Notice>
  }

  const availableYears = summary.data?.availableYears ?? []

  return (
    <section>
      {/* 這裡刻意不用願望牆那種漸層標題。那是招攬用的門面，這一頁是使用者查
          自己的東西，安靜一點比較好讀 */}
      <h1 className="text-3xl font-bold text-white">我的認領</h1>
      <p className="mt-2 text-slate-300">寄出禮物後記得回來回報，機構才知道要準備收件。</p>

      <div className="mt-6">
        <Select
          className="w-32"
          value={year}
          onChange={(event) => {
            setYear(event.target.value === '' ? '' : Number(event.target.value))
            setPage(0)
          }}
        >
          <option value="">全部年度</option>
          {availableYears.map((y) => <option key={y} value={y}>{y}</option>)}
        </Select>
      </div>

      {year !== '' && summary.data && <AnnualSummaryCard summary={summary.data} />}

      <div className="mt-6 space-y-4">
        {claims.isLoading && <Spinner label="載入認領" />}
        {claims.isError && (
          <ErrorBanner error={claims.error} onRetry={() => void claims.refetch()} />
        )}

        {claims.data?.content.length === 0 && (
          <EmptyState
            title="還沒有認領任何願望"
            hint={<Link to="/" className="text-emerald-300 underline">去願望牆看看</Link>}
          />
        )}

        {claims.data?.content.map((claim) => <ClaimRow key={claim.id} claim={claim} />)}
      </div>

      {claims.data && <Pagination page={claims.data} onChange={setPage} />}
    </section>
  )
}

/**
 * 年度小結卡。只在挑了特定年度時出現——「全部」跨年度加總沒有 cohort 的意義，
 * 數字容易被誤讀成單一年度的完成率之類的東西。
 */
function AnnualSummaryCard({ summary }: { summary: DonorAnnualSummary }) {
  return (
    <div className="glass-card mt-4 grid grid-cols-2 gap-4 p-5 sm:grid-cols-4">
      <SummaryStat value={summary.claimedCount} label="認領" />
      <SummaryStat value={summary.completedCount} label="完成" />
      <SummaryStat value={summary.childrenHelped} label="送禮孩子" unit="位" />
      <SummaryStat value={summary.organizationsSupported} label="支持機構" unit="間" />
    </div>
  )
}

function SummaryStat({ value, label, unit }: { value: number; label: string; unit?: string }) {
  return (
    <div>
      <p className="text-2xl font-bold text-white">
        {value}
        {unit && <span className="ml-1 text-base font-normal text-slate-300">{unit}</span>}
      </p>
      <p className="mt-0.5 text-sm text-slate-400">{label}</p>
    </div>
  )
}

function ClaimRow({ claim }: { claim: ClaimDonorView }) {
  const remaining = daysUntil(claim.shipDeadlineAt)
  const awaitingShipment = claim.status === 'CLAIMED'

  return (
    <Link to={`/me/claims/${claim.id}`} className="glass-card-interactive block p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-white">{claim.wishTitle}</h2>
          <p className="mt-0.5 text-sm text-slate-400">
            給 {claim.childAlias}・{claim.organizationName}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/* 比其他標籤醒目一階，但仍是半透明的——實心飽和色在玻璃上會浮出來 */}
          {claim.unreadMessageCount > 0 && (
            <span className="rounded-full border border-red-400/40 bg-red-500/20 px-2.5 py-0.5
              text-xs font-medium text-red-100">
              {claim.unreadMessageCount} 則新訊息
            </span>
          )}
          {claim.overdue && <OverdueBadge />}
          <ClaimStatusBadge status={claim.status} />
        </div>
      </div>

      <p className="mt-3 text-sm text-slate-300">
        {awaitingShipment && remaining !== null ? (
          remaining >= 0
            ? (
              <>
                請於 <strong className="font-semibold text-white">
                  {formatDate(claim.shipDeadlineAt)}
                </strong> 前寄出（還有 {remaining} 天）
              </>
            )
            : (
              <span className="text-red-300">
                已逾期 {Math.abs(remaining)} 天，機構可能會收回這個願望
              </span>
            )
        ) : (
          <span className="text-slate-400">
            認領於 {formatDate(claim.claimedAt)}・{CLAIM_STATUS_LABELS[claim.status]}
          </span>
        )}
      </p>
    </Link>
  )
}
