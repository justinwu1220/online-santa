import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../lib/api'
import { useAuth } from '../lib/authContext'
import type { ClaimDonorView, WishPublicView } from '../lib/types'
import { effectiveRoleOf, useCurrentUser } from '../lib/useCurrentUser'
import { Breadcrumb } from '../components/Breadcrumb'
import { ErrorBanner, Notice, Spinner } from '../components/Feedback'
import { Button, TextArea } from '../components/Form'
import { WishStatusBadge } from '../components/StatusBadge'
import { wishIcon } from '../lib/wishIcon'

export function WishDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { email, emailVerified } = useAuth()
  const me = useCurrentUser()
  const [message, setMessage] = useState('')

  const wish = useQuery({
    queryKey: ['wish', id],
    queryFn: () => api.get<WishPublicView>(`/api/wishes/${id}`),
  })

  const claim = useMutation({
    mutationFn: () => api.post<ClaimDonorView>(`/api/wishes/${id}/claim`,
      message.trim() ? { donorMessage: message.trim() } : undefined),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ['wishes'] })
      void queryClient.invalidateQueries({ queryKey: ['claims'] })
      navigate(`/me/claims/${created.id}`)
    },
    onError: (error) => {
      // 搶輸了：立刻把這一頁的願望狀態更新成最新的，
      // 使用者才不會對著一個已經被領走的按鈕反覆點擊
      if (error instanceof ApiError && error.isAlreadyClaimed) {
        void queryClient.invalidateQueries({ queryKey: ['wish', id] })
        void queryClient.invalidateQueries({ queryKey: ['wishes'] })
      }
    },
  })

  if (wish.isLoading) return <Spinner label="載入願望" />
  if (wish.isError) {
    const error = wish.error
    if (error instanceof ApiError && error.isNotFound) {
      return (
        <section className="py-16 text-center">
          <p className="text-5xl">🔍</p>
          <h1 className="mt-4 text-2xl font-bold text-white">找不到這個願望</h1>
          <p className="mt-2 text-slate-400">它可能已經下架了。</p>
          <Link to="/" className="mt-6 inline-block text-emerald-300 underline">回到願望牆</Link>
        </section>
      )
    }
    return <ErrorBanner error={error} onRetry={() => void wish.refetch()} />
  }

  const data = wish.data!
  const available = data.status === 'AVAILABLE'
  const isDonor = effectiveRoleOf(me.data) === 'DONOR'

  return (
    <div className="space-y-6">
      {/* 願望連結常被分享出去，直接落在這一頁的人沒有上一頁可回 */}
      <Breadcrumb items={[{ label: '願望牆', to: '/' }, { label: data.title }]} />

      <article className="grid gap-8 lg:grid-cols-[1.1fr_1fr]">
        <div className="glass-card overflow-hidden">
          {data.imageUrl ? (
            <img src={data.imageUrl} alt="" className="aspect-[4/3] w-full object-cover" />
          ) : (
            <div className="flex aspect-[4/3] items-center justify-center text-7xl">
              {wishIcon(data.category)}
            </div>
          )}
        </div>

        <div>
          <div className="flex items-start justify-between gap-4">
            <h1 className="text-3xl font-bold text-white">{data.title}</h1>
            <WishStatusBadge status={data.status} />
          </div>

          <dl className="glass-card mt-6 grid grid-cols-2 gap-4 p-4 text-sm">
            <Detail label="孩子" value={data.childAlias} />
            <Detail label="年齡" value={data.ageRangeLabel} />
            <Detail label="分類" value={data.categoryLabel} />
            <Detail label="預算" value={data.priceRangeLabel} />
            {data.interests && (
              <div className="col-span-2">
                <Detail label="喜歡的東西" value={data.interests} />
              </div>
            )}
            <div className="col-span-2">
              <Detail label="來自" value={data.organizationName} />
            </div>
          </dl>

          {data.description && (
            <p className="mt-6 whitespace-pre-wrap leading-relaxed text-slate-300">
              {data.description}
            </p>
          )}

          <div className="mt-8">
            {!available ? (
              <Notice tone="warning">
                這個願望已經被認領了。<Link to="/" className="underline">看看其他願望</Link>
              </Notice>
            ) : !email ? (
              <Notice>
                <Link to={`/login?next=${encodeURIComponent(`/wishes/${id}`)}`}
                  className="font-medium underline">登入或註冊</Link>
                　之後就能認領這個願望。登入後會回到這一頁。
              </Notice>
            ) : !emailVerified ? (
              // 機構要靠這個信箱聯繫捐贈者——後端也會擋，這裡先說清楚原因
              <Notice tone="warning">
                請先完成信箱驗證才能認領。上方的橫幅可以重新寄送驗證信，
                驗證完點「我已經驗證好了」即可。
              </Notice>
            ) : !isDonor ? (
              <Notice tone="warning">
                機構成員與管理員無法認領願望，請改用一般民眾的帳號。
              </Notice>
            ) : (
              <div className="space-y-3">
                <TextArea
                  rows={3}
                  maxLength={500}
                  value={message}
                  placeholder="想對孩子說的話（選填）"
                  onChange={(event) => setMessage(event.target.value)}
                />
                {claim.isError && <ClaimError error={claim.error} />}
                <Button
                  className="w-full py-3 text-base"
                  disabled={claim.isPending}
                  onClick={() => claim.mutate()}
                >
                  {claim.isPending ? '認領中…' : '我要實現這個願望'}
                </Button>
                <p className="text-center text-xs text-slate-400">
                  認領後請於期限內寄出禮物，逾期機構可能會收回讓其他人認領。
                </p>
              </div>
            )}
          </div>
        </div>
      </article>
    </div>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-400">{label}</dt>
      <dd className="mt-0.5 font-medium text-white">{value}</dd>
    </div>
  )
}

/** 搶輸的情境值得一個專屬的訊息，而不是通用的錯誤提示。 */
function ClaimError({ error }: { error: unknown }) {
  if (error instanceof ApiError && error.isAlreadyClaimed) {
    return (
      <div className="rounded-lg border border-amber-400/25 bg-amber-400/10 px-4 py-3
        text-sm text-amber-100">
        <p className="font-medium">手慢了一步 🎄</p>
        <p className="mt-1">
          這個願望剛剛被其他人領走了。
          <Link to="/" className="ml-1 underline">回願望牆看看其他孩子</Link>
        </p>
      </div>
    )
  }
  return <ErrorBanner error={error} />
}
