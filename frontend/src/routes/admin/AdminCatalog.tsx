import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, withQuery } from '../../lib/api'
import { formatDate } from '../../lib/format'
import type {
  AdminClaimView, AdminWishView, ClaimStatus, PageResponse, WishStatus,
} from '../../lib/types'
import { ConsolePanel } from '../../components/layouts/ConsoleLayout'
import { EmptyState, ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { Select } from '../../components/Form'
import { Pagination } from '../../components/Pagination'
import { ClaimStatusBadge, OverdueBadge, WishStatusBadge } from '../../components/StatusBadge'

const WISH_FILTERS: { value: WishStatus | ''; label: string }[] = [
  { value: '', label: '全部狀態' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'AVAILABLE', label: '上架中' },
  { value: 'CLAIMED', label: '已認領' },
  { value: 'FULFILLED', label: '已完成' },
  { value: 'ARCHIVED', label: '已下架' },
]

const CLAIM_FILTERS: { value: ClaimStatus | ''; label: string }[] = [
  { value: '', label: '全部狀態' },
  { value: 'CLAIMED', label: '待寄送' },
  { value: 'SHIPPED', label: '運送中' },
  { value: 'RECEIVED', label: '已收到' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'RELEASED', label: '已收回' },
  { value: 'CANCELLED', label: '已取消' },
]

/** 跨機構的願望檢視。含所有機構的草稿——機構自己只看得到自家的。 */
export function AdminWishes() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [page, setPage] = useState(0)
  const status = (searchParams.get('status') ?? '') as WishStatus | ''

  const wishes = useQuery({
    queryKey: ['admin-wishes', status, page],
    queryFn: () => api.get<PageResponse<AdminWishView>>(
      withQuery('/api/admin/wishes', { status, page, size: 20 })),
  })

  return (
    <ConsolePanel
      title="全站願望"
      action={
        <Select
          className="w-36"
          value={status}
          onChange={(event) => {
            setSearchParams(event.target.value ? { status: event.target.value } : {})
            setPage(0)
          }}
        >
          {WISH_FILTERS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </Select>
      }
    >
      {wishes.isLoading && <Spinner label="載入願望" />}
      {wishes.isError && <ErrorBanner error={wishes.error} onRetry={() => void wishes.refetch()} />}
      {wishes.data?.content.length === 0 && <EmptyState title="沒有符合條件的願望" />}

      {(wishes.data?.content.length ?? 0) > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-slate-200 text-left text-xs text-slate-500">
              <tr>
                <Th>願望</Th><Th>孩子</Th><Th>機構</Th><Th>狀態</Th><Th>建立</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {wishes.data?.content.map((wish) => (
                <tr key={wish.id} className="hover:bg-slate-50">
                  <Td className="font-medium text-slate-800">{wish.title}</Td>
                  <Td>{wish.childAlias}</Td>
                  <Td className="text-slate-500">{wish.organizationName}</Td>
                  <Td><WishStatusBadge status={wish.status} /></Td>
                  <Td className="text-slate-400">{formatDate(wish.createdAt)}</Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {wishes.data && <Pagination page={wishes.data} onChange={setPage} />}
    </ConsolePanel>
  )
}

/** 跨機構的認領檢視。點進單筆會寫入稽核紀錄。 */
export function AdminClaims() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [page, setPage] = useState(0)
  const status = (searchParams.get('status') ?? '') as ClaimStatus | ''
  const overdueOnly = searchParams.get('overdue') === 'true'

  const claims = useQuery({
    queryKey: ['admin-claims', status, overdueOnly, page],
    queryFn: () => api.get<PageResponse<AdminClaimView>>(
      withQuery('/api/admin/claims', {
        status: overdueOnly ? undefined : status,
        overdue: overdueOnly ? 'true' : undefined,
        page,
        size: 20,
      })),
  })

  return (
    <ConsolePanel
      title={overdueOnly ? '逾期未寄送的認領' : '全站認領'}
      action={
        <div className="flex items-center gap-2">
          <Select
            className="w-36"
            value={overdueOnly ? 'overdue' : status}
            onChange={(event) => {
              const value = event.target.value
              setSearchParams(
                value === 'overdue' ? { overdue: 'true' } : value ? { status: value } : {})
              setPage(0)
            }}
          >
            {CLAIM_FILTERS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
            <option value="overdue">只看逾期</option>
          </Select>
        </div>
      }
    >
      <div className="mb-4">
        <Notice tone="warning">
          點進單筆認領會看到捐贈者的聯絡資訊與照片，
          <strong>這個操作會寫入稽核紀錄</strong>，可在「系統與稽核」查看。
        </Notice>
      </div>

      {claims.isLoading && <Spinner label="載入認領" />}
      {claims.isError && <ErrorBanner error={claims.error} onRetry={() => void claims.refetch()} />}
      {claims.data?.content.length === 0 && (
        <EmptyState icon={overdueOnly ? '✅' : '📦'}
          title={overdueOnly ? '沒有逾期的認領' : '沒有符合條件的認領'} />
      )}

      {(claims.data?.content.length ?? 0) > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-slate-200 text-left text-xs text-slate-500">
              <tr>
                <Th>願望</Th><Th>機構</Th><Th>狀態</Th><Th>認領於</Th><Th></Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {claims.data?.content.map((claim) => (
                <tr key={claim.id} className="hover:bg-slate-50">
                  <Td className="font-medium text-slate-800">{claim.wishTitle}</Td>
                  <Td className="text-slate-500">{claim.organizationName}</Td>
                  <Td>
                    <span className="flex items-center gap-1.5">
                      <ClaimStatusBadge status={claim.status} />
                      {claim.overdue && <OverdueBadge />}
                    </span>
                  </Td>
                  <Td className="text-slate-400">{formatDate(claim.claimedAt)}</Td>
                  <Td>
                    <Link to={`/admin/claims/${claim.id}`}
                      className="text-santa-700 hover:underline">
                      詳情
                    </Link>
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {claims.data && <Pagination page={claims.data} onChange={setPage} />}
    </ConsolePanel>
  )
}

const Th = ({ children }: { children?: React.ReactNode }) =>
  <th className="px-3 py-2 font-medium">{children}</th>

const Td = ({ children, className = '' }: { children?: React.ReactNode; className?: string }) =>
  <td className={`px-3 py-2.5 ${className}`}>{children}</td>
