import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../lib/api'
import { avatarFor } from '../lib/avatar'
import { useAuth } from '../lib/authContext'
import type { ClaimDonorView, WishPublicView } from '../lib/types'
import { effectiveRoleOf, useCurrentUser } from '../lib/useCurrentUser'
import { publicStatusOf } from '../lib/wishStatus'
import { WishFeature } from '../components/WishFeature'
import { Dialog } from '../components/ui/Dialog'
import { TextArea } from '../components/ui/Fields'
import { ErrorNotice, Spinner } from '../components/ui/Spinner'

export function WishDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { email, emailVerified } = useAuth()
  const me = useCurrentUser()
  const [message, setMessage] = useState('')
  const [confirming, setConfirming] = useState(false)

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
      setConfirming(false)
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
        <div className="empty" style={{ marginTop: 48 }}>
          <p aria-hidden style={{ fontSize: 40, margin: 0 }}>🔍</p>
          <p style={{ margin: '8px 0 0', fontSize: 22, fontWeight: 900, color: 'var(--fg)' }}>找不到這個願望</p>
          <p style={{ margin: '6px 0 0' }}>它可能已經下架了。</p>
          <Link to="/" className="btn">回到願望牆</Link>
        </div>
      )
    }
    return <ErrorNotice error={error} onRetry={() => void wish.refetch()} />
  }

  const data = wish.data!
  const status = publicStatusOf(data.status)
  const available = data.status === 'AVAILABLE'
  const isDonor = effectiveRoleOf(me.data) === 'DONOR'
  const avatar = avatarFor(data.id, data.ageRange)

  return (
    <div data-status={status.key}>
      {/* 願望連結常被分享出去，直接落在這一頁的人沒有上一頁可回 */}
      <nav className="crumbs" aria-label="麵包屑">
        <Link to="/">願望牆</Link>
        <ChevronRight className="icon" size={14} aria-hidden />
        <span aria-current="page">{data.title}</span>
      </nav>

      <WishFeature
        wish={data}
        statusKey={status.key}
        statusLabel={status.label}
        orgAnchor="org"
        footer={(
          <ClaimZone
            available={available}
            status={status.key}
            loggedIn={Boolean(email)}
            verified={emailVerified}
            isDonor={isDonor}
            wishId={id}
            onClaim={() => setConfirming(true)}
          />
        )}
      />

      <section className="org" id="org" aria-label="機構">
        <div className="org__body">
          <span className="org__eyebrow">這份願望來自</span>
          <h2 className="org__name">{data.organizationName}</h2>
        </div>
      </section>

      <Dialog open={confirming} onClose={() => !claim.isPending && setConfirming(false)}
        dismissible={!claim.isPending} labelledBy="claim-title">
        <h2 id="claim-title">確定要認領這個願望嗎？</h2>
        <div className="dialog__gift">
          <img src={avatar} alt="" />
          <div>
            <strong>{data.title}</strong>
            <span>給 {data.childAlias}・{data.organizationName}</span>
          </div>
        </div>
        <p>認領後請於期限內寄出禮物，逾期機構可能會收回讓其他人認領。</p>
        <TextArea rows={3} maxLength={500} value={message}
          placeholder="想對孩子說的話（選填）"
          onChange={(event) => setMessage(event.target.value)} />
        {claim.isError && <div style={{ marginTop: 12 }}><ClaimError error={claim.error} /></div>}
        <div className="dialog__actions">
          <button type="button" className="btn btn--ghost" disabled={claim.isPending}
            onClick={() => setConfirming(false)}>
            再想想
          </button>
          <button type="button" className="btn btn--primary" disabled={claim.isPending}
            onClick={() => claim.mutate()}>
            {claim.isPending ? '認領中…' : '確定認領'}
          </button>
        </div>
      </Dialog>
    </div>
  )
}

/**
 * 資訊欄最下方的行動區，依狀態與使用者身分決定。
 * 機構與管理員帳號不能認領（SPEC 15.9）：按鈕停用，說明原因；後端也會拒絕。
 */
function ClaimZone({ available, status, loggedIn, verified, isDonor, wishId, onClaim }: {
  available: boolean
  status: 'open' | 'claimed' | 'done'
  loggedIn: boolean
  verified: boolean
  isDonor: boolean
  wishId: string
  onClaim: () => void
}) {
  if (!available) {
    return status === 'claimed' ? (
      <div className="notice notice--claimed">
        這個願望已經被認領了，正在等待禮物寄出。
        <p><Link to="/">看看其他還在等待的願望</Link></p>
      </div>
    ) : (
      <div className="notice notice--done">
        這個願望已經完成了，謝謝每一位伸出手的人。
        <p><Link to="/">看看其他還在等待的願望</Link></p>
      </div>
    )
  }

  if (!loggedIn) {
    return (
      <div>
        <Link className="cta" to={`/login?next=${encodeURIComponent(`/wishes/${wishId}`)}`}>
          登入後認領這個願望
        </Link>
        <p className="cta-help">登入或註冊之後就能認領，登入後會回到這一頁。</p>
      </div>
    )
  }

  if (!verified) {
    // 機構要靠這個信箱聯繫捐贈者——後端也會擋，這裡先說清楚原因
    return (
      <div className="notice notice--warn">
        請先完成信箱驗證才能認領。上方的提示列可以重新寄送驗證信，驗證完點「我已經驗證好了」即可。
      </div>
    )
  }

  return (
    <div>
      <button type="button" className="cta" disabled={!isDonor} onClick={onClaim}>
        認領這個願望
      </button>
      <p className="cta-help">
        {isDonor
          ? '認領後請於期限內寄出禮物，逾期機構可能會收回讓其他人認領。'
          : '機構與管理員帳號不能認領願望，請用個人帳號參與。'}
      </p>
    </div>
  )
}

/** 搶輸的情境值得一個專屬的訊息，而不是通用的錯誤提示。 */
function ClaimError({ error }: { error: unknown }) {
  if (error instanceof ApiError && error.isAlreadyClaimed) {
    return (
      <div className="notice notice--warn">
        <p><strong>手慢了一步 🎄</strong></p>
        <p>
          這個願望剛剛被其他人領走了。<Link to="/">回願望牆看看其他孩子</Link>
        </p>
      </div>
    )
  }
  return <ErrorNotice error={error} />
}
