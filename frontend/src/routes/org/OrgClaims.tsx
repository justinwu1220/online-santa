import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, withQuery } from '../../lib/api'
import { daysUntil, formatDate } from '../../lib/format'
import type { ClaimOrgView, ClaimStatus, PageResponse } from '../../lib/types'
import { EmptyState, ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { Select } from '../../components/Form'
import { Pagination } from '../../components/Pagination'
import { ClaimStatusBadge, OverdueBadge } from '../../components/StatusBadge'
import { ReceiveGiftControl } from './ReceiveGiftControl'

const STATUS_FILTERS: { value: ClaimStatus | ''; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'CLAIMED', label: '待寄送' },
  { value: 'SHIPPED', label: '運送中' },
  { value: 'RECEIVED', label: '已收到' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'RELEASED', label: '已收回' },
  { value: 'CANCELLED', label: '已取消' },
]

/**
 * 機構的認領管理。`overdueOnly` 時改看逾期清單。
 *
 * <p>逾期清單刻意沒有年度篩選——那是待辦清單，手動釋回政策機構的跨年舊案就該
 * 持續出現，加年度篩選會把該處理的事藏起來（見後端 ClaimService 的說明）。
 */
export function OrgClaims({ overdueOnly = false }: { overdueOnly?: boolean }) {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const status = (searchParams.get('status') ?? '') as ClaimStatus | ''
  const year = overdueOnly ? '' : (searchParams.get('year') ?? '')
  const [page, setPage] = useState(0)

  const path = overdueOnly
    ? '/api/organizations/me/claims/overdue'
    : '/api/organizations/me/claims'

  // 逾期清單不篩年度，這支下拉的選項也就不需要在逾期模式下打
  const years = useQuery({
    queryKey: ['claims', 'org', 'years'],
    queryFn: () => api.get<number[]>('/api/organizations/me/claims/years'),
    staleTime: 30_000,
    enabled: !overdueOnly,
  })

  const claims = useQuery({
    queryKey: ['claims', 'org', overdueOnly, status, year, page],
    queryFn: () => api.get<PageResponse<ClaimOrgView>>(
      withQuery(path, {
        status: overdueOnly ? undefined : status,
        year: overdueOnly ? undefined : (year || undefined),
        page,
        size: 10,
      })),
  })

  /** status／year 各自獨立存在網址上，換一個篩選不該把另一個洗掉。 */
  const setFilter = (key: 'status' | 'year', value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    setSearchParams(next)
    setPage(0)
  }

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['claims'] })
    void queryClient.invalidateQueries({ queryKey: ['org-wishes'] })
  }

  return (
    <div className="space-y-5">
      {overdueOnly ? (
        <Notice tone="warning">
          這些認領已超過寄送期限。建議先透過對話聯繫捐贈者；聯繫不上再收回，
          讓願望回到願望牆給其他人認領。
        </Notice>
      ) : (
        <div className="flex items-center gap-2">
          <Select className="w-40" value={status}
            onChange={(event) => setFilter('status', event.target.value)}>
            {STATUS_FILTERS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </Select>
          <Select className="w-28" value={year}
            onChange={(event) => setFilter('year', event.target.value)}>
            <option value="">全部年度</option>
            {(years.data ?? []).map((y) => (
              <option key={y} value={y}>{y}</option>
            ))}
          </Select>
        </div>
      )}

      {claims.isLoading && <Spinner label="載入認領" />}
      {claims.isError && <ErrorBanner error={claims.error} onRetry={() => void claims.refetch()} />}

      {claims.data?.content.length === 0 && (
        <EmptyState
          icon={overdueOnly ? '✅' : '📦'}
          title={overdueOnly ? '沒有逾期的認領' : '還沒有人認領'}
          hint={overdueOnly ? '所有認領都在期限內。' : '願望上架後就會出現在這裡。'}
        />
      )}

      <div className="space-y-4">
        {claims.data?.content.map((claim) => (
          <ClaimCard key={claim.id} claim={claim} onChanged={refresh} />
        ))}
      </div>

      {claims.data && <Pagination page={claims.data} onChange={setPage} />}
    </div>
  )
}

function ClaimCard({ claim, onChanged }: { claim: ClaimOrgView; onChanged: () => void }) {
  const remaining = daysUntil(claim.shipDeadlineAt)

  return (
    // relative：標題連結用 after 偽元素撐滿整張卡片，點卡片任何地方都進詳情；
    // 卡片上的按鈕要 relative z-10 才不會被蓋住
    <div className="relative rounded-xl bg-white p-5 ring-1 ring-santa-100 transition-shadow
      hover:ring-santa-300">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-slate-800">
            <Link to={`/org/claims/${claim.id}`}
              className="after:absolute after:inset-0 after:content-[''] hover:underline">
              {claim.wishTitle}
            </Link>
          </h3>
          <p className="mt-0.5 text-sm text-slate-500">
            給 {claim.childAlias}・認領者 {claim.donorName ?? claim.donorEmail}
          </p>
          <p className="text-sm text-slate-400">{claim.donorEmail}</p>
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
        認領於 {formatDate(claim.claimedAt)}
        {claim.status === 'CLAIMED' && remaining !== null && (
          remaining >= 0
            ? `・還有 ${remaining} 天可寄送`
            : `・已逾期 ${Math.abs(remaining)} 天`
        )}
        {claim.trackingNumber && `・${claim.trackingCarrier} ${claim.trackingNumber}`}
      </p>

      {claim.status === 'SHIPPED' && (
        <div className="relative z-10 mt-4 flex flex-wrap items-center gap-2">
          <ReceiveGiftControl claim={claim} onChanged={onChanged} />
        </div>
      )}
    </div>
  )
}
