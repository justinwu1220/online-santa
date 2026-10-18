import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, ChevronRight } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../lib/api'
import { avatarFor } from '../lib/avatar'
import { useAuth } from '../lib/authContext'
import type { ClaimDonorView, WishPublicView } from '../lib/types'
import {
  burst, center, isCurrentNav, markSpecialNavigation, reducedMotion, rise, startNav, wait,
} from '../lib/motion'
import { effectiveRoleOf, useCurrentUser } from '../lib/useCurrentUser'
import { useDetailEntry } from '../lib/useEntryAnimation'
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
  const [claimedOk, setClaimedOk] = useState(false)
  const location = useLocation()
  const root = useRef<HTMLDivElement>(null)
  // 從「取消認領」回來：標籤彈回、認領按鈕區浮上（SPEC 11.4），不是一般的進場
  const arrivedByCancel = Boolean((location.state as { cancelled?: boolean } | null)?.cancelled)

  const wish = useQuery({
    queryKey: ['wish', id],
    queryFn: () => api.get<WishPublicView>(`/api/wishes/${id}`),
  })

  const claim = useMutation({
    mutationFn: () => api.post<ClaimDonorView>(`/api/wishes/${id}/claim`,
      message.trim() ? { donorMessage: message.trim() } : undefined),
    onSuccess: (created) => {
      setConfirming(false)
      void celebrate(created.id)
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

  /**
   * 認領成功動畫（SPEC 11.3）：資料在 POST 成功當下已經寫入，這裡只是疊上去的效果。
   * 按鈕變金色「✓ 認領成功」並彈一下、散出星星；650ms 後換到認領後頁面（那一頁接著播緞帶與蝴蝶結）。
   * 願望與認領的查詢延到換頁前才重新抓——太早的話，這一頁的按鈕會在動畫中途被換掉。
   */
  async function celebrate(claimId: string) {
    const serial = startNav()
    setClaimedOk(true)
    const button = document.getElementById('claim-cta')
    if (button && !reducedMotion()) {
      burst(...center(button), 22, 140)
      button.animate(
        [{ transform: 'scale(1)' }, { transform: 'scale(1.04)' }, { transform: 'scale(1)' }],
        { duration: 420, easing: 'ease-out' })
    }
    await wait(reducedMotion() ? 0 : 650)
    void queryClient.invalidateQueries({ queryKey: ['wishes'] })
    void queryClient.invalidateQueries({ queryKey: ['claims'] })
    void queryClient.invalidateQueries({ queryKey: ['wish', id] })
    // 中途已經換到別頁（連點、按上一頁）：資料已經寫入，不要再硬把人拉過去
    if (!isCurrentNav(serial)) return
    markSpecialNavigation()
    navigate(`/me/claims/${claimId}`, { state: { celebrate: true } })
  }

  // 進場：拆信轉場過來要對齊遮罩淡出的時間點；直接開網址只讓大卡片浮上
  useDetailEntry(root, wish.isSuccess, !arrivedByCancel)

  useEffect(() => {
    if (!arrivedByCancel || !wish.data || !root.current) return
    if (!reducedMotion()) {
      root.current.querySelector('.status-tab')?.animate(
        [
          { opacity: 0, transform: 'scale(.6)' },
          { opacity: 1, transform: 'scale(1.12)', offset: 0.6 },
          { opacity: 1, transform: 'none' },
        ],
        { duration: 480, delay: 200, easing: 'ease-out', fill: 'backwards' })
      rise([root.current.querySelector('.info > :last-child')], { delay: 280, stagger: 0, dist: 14, dur: 480 })
    }
    // 把「剛取消」的標記清掉，重新整理就不會再播一次
    markSpecialNavigation()
    navigate(location.pathname, { replace: true, state: null })
  }, [arrivedByCancel, wish.data, navigate, location.pathname])

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
    <div data-status={status.key} ref={root}>
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
            success={claimedOk}
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
function ClaimZone({ available, status, loggedIn, verified, isDonor, wishId, success, onClaim }: {
  available: boolean
  status: 'open' | 'claimed' | 'done'
  loggedIn: boolean
  verified: boolean
  isDonor: boolean
  wishId: string
  /** 認領剛成功：按鈕變金色「✓ 認領成功」 */
  success?: boolean
  onClaim: () => void
}) {
  if (!available && !success) {
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
      <button type="button" id="claim-cta" className={`cta${success ? ' is-success' : ''}`}
        disabled={!isDonor || success} onClick={onClaim}>
        {success ? <><Check className="icon" aria-hidden />認領成功</> : '認領這個願望'}
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
