import { useQuery } from '@tanstack/react-query'
import { Gift } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, withQuery } from '../lib/api'
import { avatarSrc } from '../lib/avatar'
import { useAuth } from '../lib/authContext'
import { daysUntil, formatDate } from '../lib/format'
import { useListEntry } from '../lib/useEntryAnimation'
import type { ClaimDonorView, PageResponse } from '../lib/types'
import { claimTabOf } from '../lib/wishStatus'
import { Pager } from '../components/ui/Pager'
import { ErrorNotice, Spinner } from '../components/ui/Spinner'

/** 一次取回的筆數。原型是一次列出全部，沒有分頁；認領者的認領數遠小於這個數字。 */
const PAGE_SIZE = 100

/** 進度由前面到後面：待寄送 → 已寄出 → 機構已收到 → 流程完成，已收回／已取消的放最後（原型依進度排序）。 */
const PROGRESS_ORDER: Record<string, number> = {
  CLAIMED: 0, SHIPPED: 1, RECEIVED: 2, COMPLETED: 3, RELEASED: 4, CANCELLED: 4,
}

export function MyClaims() {
  const { email } = useAuth()
  const [page, setPage] = useState(0)

  const claims = useQuery({
    queryKey: ['claims', 'mine', page],
    queryFn: () => api.get<PageResponse<ClaimDonorView>>(
      withQuery('/api/claims/me', { page, size: PAGE_SIZE })),
    enabled: Boolean(email),
  })

  useListEntry('/me/claims', Boolean(claims.data))

  if (!email) {
    return <div className="notice notice--info" style={{ marginTop: 40 }}>請先在右上角登入，才能查看自己的認領。</div>
  }

  // Array.prototype.sort 是穩定排序：同進度維持後端原本的順序
  const sorted = [...(claims.data?.content ?? [])]
    .sort((a, b) => (PROGRESS_ORDER[a.status] ?? 5) - (PROGRESS_ORDER[b.status] ?? 5))

  return (
    <section>
      <div className="list-head">
        <h1>我的認領</h1>
        <p>你認領的願望都在這裡，點進去可以查看寄送資訊、回報寄送和機構對話。</p>
      </div>

      {claims.isLoading && <Spinner label="載入認領" />}
      {claims.isError && <ErrorNotice error={claims.error} onRetry={() => void claims.refetch()} />}

      {claims.data?.content.length === 0 && (
        <div className="empty">
          你還沒有認領任何願望。
          <div><Link to="/" className="btn">去願望牆看看</Link></div>
        </div>
      )}

      {sorted.length > 0 && (
        <div className="wish-grid">
          {sorted.map((claim) => <ClaimCard key={claim.id} claim={claim} />)}
        </div>
      )}

      {claims.data && <Pager page={claims.data} onChange={setPage} />}
    </section>
  )
}

/** 同願望卡，標籤顯示「自己的進度」而不是願望的狀態（SPEC 11.2）。 */
function ClaimCard({ claim }: { claim: ClaimDonorView }) {
  const tab = claimTabOf(claim.status)
  const remaining = daysUntil(claim.shipDeadlineAt)
  const awaitingShipment = claim.status === 'CLAIMED'
  // 願望被管理員凍結：標籤與說明改成「暫停處理中」
  const paused = claim.paused && awaitingShipment

  return (
    <Link
      to={`/me/claims/${claim.id}`}
      className="wish-card"
      data-child={claim.childAlias}
      data-status={tab.key}
      aria-label={`${claim.childAlias}，${claim.ageRangeLabel}，${claim.organizationName}，想要${claim.wishTitle}，${tab.label}`}
    >
      <span className="status-tab">{paused ? '暫停處理中' : tab.label}</span>

      <div className="who">
        <img className="avatar" src={avatarSrc(claim.avatar)} alt="" width={64} height={64} />
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
          {paused
            ? '平台正在確認這個願望，暫時不用寄出，寄送期限暫停計算'
            : awaitingShipment && remaining !== null
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
