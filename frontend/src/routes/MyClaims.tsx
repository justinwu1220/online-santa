import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, withQuery } from '../lib/api'
import { useAuth } from '../lib/authContext'
import { CLAIM_STATUS_LABELS, daysUntil, formatDate } from '../lib/format'
import type { ClaimDonorView, PageResponse } from '../lib/types'
import { EmptyState, ErrorBanner, Notice, Spinner } from '../components/Feedback'
import { Pagination } from '../components/Pagination'
import { ClaimStatusBadge, OverdueBadge } from '../components/StatusBadge'

export function MyClaims() {
  const { email } = useAuth()
  const [page, setPage] = useState(0)

  const claims = useQuery({
    queryKey: ['claims', 'mine', page],
    queryFn: () => api.get<PageResponse<ClaimDonorView>>(
      withQuery('/api/claims/me', { page, size: 10 })),
    enabled: Boolean(email),
  })

  if (!email) {
    return <Notice>請先在右上角登入，才能查看自己的認領。</Notice>
  }

  return (
    <section>
      <h1 className="text-3xl font-bold text-santa-700">我的認領</h1>
      <p className="mt-2 text-slate-600">寄出禮物後記得回來回報，機構才知道要準備收件。</p>

      <div className="mt-6 space-y-4">
        {claims.isLoading && <Spinner label="載入認領" />}
        {claims.isError && (
          <ErrorBanner error={claims.error} onRetry={() => void claims.refetch()} />
        )}

        {claims.data?.content.length === 0 && (
          <EmptyState
            title="還沒有認領任何願望"
            hint={<Link to="/" className="text-berry-600 underline">去願望牆看看</Link>}
          />
        )}

        {claims.data?.content.map((claim) => <ClaimRow key={claim.id} claim={claim} />)}
      </div>

      {claims.data && <Pagination page={claims.data} onChange={setPage} />}
    </section>
  )
}

function ClaimRow({ claim }: { claim: ClaimDonorView }) {
  const remaining = daysUntil(claim.shipDeadlineAt)
  const awaitingShipment = claim.status === 'CLAIMED'

  return (
    <Link
      to={`/me/claims/${claim.id}`}
      className="block rounded-xl bg-white p-5 ring-1 ring-santa-100 transition-shadow
        hover:shadow-md focus:outline-none focus:ring-2 focus:ring-santa-500"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-slate-800">{claim.wishTitle}</h2>
          <p className="mt-0.5 text-sm text-slate-500">
            給 {claim.childAlias}・{claim.organizationName}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {claim.unreadMessageCount > 0 && (
            <span className="rounded-full bg-berry-500 px-2 py-0.5 text-xs font-medium text-white">
              {claim.unreadMessageCount} 則新訊息
            </span>
          )}
          {claim.overdue && <OverdueBadge />}
          <ClaimStatusBadge status={claim.status} />
        </div>
      </div>

      <p className="mt-3 text-sm text-slate-500">
        {awaitingShipment && remaining !== null ? (
          remaining >= 0
            ? <>請於 <strong className="text-slate-700">{formatDate(claim.shipDeadlineAt)}</strong> 前寄出（還有 {remaining} 天）</>
            : <span className="text-berry-600">已逾期 {Math.abs(remaining)} 天，機構可能會收回這個願望</span>
        ) : (
          <>認領於 {formatDate(claim.claimedAt)}・{CLAIM_STATUS_LABELS[claim.status]}</>
        )}
      </p>
    </Link>
  )
}
