import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight, Gift } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { playCancelOut, playCelebration } from '../lib/claimMotion'
import { daysUntil } from '../lib/format'
import { clearEffects, isCurrentNav, markSpecialNavigation, startNav } from '../lib/motion'
import type { AttachmentView, ClaimDonorView, WishPublicView } from '../lib/types'
import { useDetailEntry } from '../lib/useEntryAnimation'
import { useMyProfile } from '../lib/useMyProfile'
import { claimTabOf } from '../lib/wishStatus'
import { OrgPhoto, OrgSiteLink } from '../components/OrgBits'
import { WishFeature } from '../components/WishFeature'
import { Chat } from '../components/ui/Chat'
import { ErrorNotice, Spinner } from '../components/ui/Spinner'
import { useToast } from '../components/ui/toastContext'
import {
  AddressPanel, CancelPanel, DeadlineBar, ProgressSteps, ProofGrid, ShipSummary, StepPanel,
} from './claim/ClaimParts'
import { ShipForm } from './claim/ShipForm'

const CLOSED_STATUSES = ['COMPLETED', 'RELEASED', 'CANCELLED']

/**
 * 認領後頁面（SPEC 11）：願望大卡片＋「你的進度」，下方依序是期限提示、
 * ① 把禮物寄到這裡、② 回報寄送、③ 有問題就問機構；右欄是機構小卡與取消認領。
 */
export function ClaimDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const profile = useMyProfile()
  const location = useLocation()
  const root = useRef<HTMLDivElement>(null)
  // 剛認領成功換過來：播緞帶與蝴蝶結（SPEC 11.3），這一頁就不再播一般的進場
  const celebrate = Boolean((location.state as { celebrate?: boolean } | null)?.celebrate)

  const claim = useQuery({
    queryKey: ['claim', id],
    queryFn: () => api.get<ClaimDonorView>(`/api/claims/${id}`),
  })

  const wishId = claim.data?.wishId
  // 願望大卡片要的信件照片與說明在公開的願望資料裡
  const wish = useQuery({
    queryKey: ['wish', wishId],
    queryFn: () => api.get<WishPublicView>(`/api/wishes/${wishId}`),
    enabled: Boolean(wishId),
  })

  const attachments = useQuery({
    queryKey: ['claim', id, 'attachments'],
    queryFn: () => api.get<AttachmentView[]>(`/api/claims/${id}/attachments`),
  })

  // 願望大卡片要等公開的願望資料到了（成功或失敗）才算準備好，否則動畫會播在稍後被換掉的暫代版面上
  const ready = claim.isSuccess && !wish.isLoading
  useDetailEntry(root, ready, !celebrate)

  useEffect(() => {
    if (!celebrate || !ready || !root.current) return
    playCelebration(root.current)
    // 把「剛認領」的標記清掉，重新整理就不會再播一次
    markSpecialNavigation()
    navigate(location.pathname, { replace: true, state: null })
  }, [celebrate, ready, navigate, location.pathname])

  function refreshAll() {
    void queryClient.invalidateQueries({ queryKey: ['claim', id] })
    void queryClient.invalidateQueries({ queryKey: ['claim', id, 'attachments'] })
    void queryClient.invalidateQueries({ queryKey: ['claims'] })
  }

  if (claim.isLoading) return <Spinner label="載入認領" />
  if (claim.isError) {
    return <ErrorNotice error={claim.error} onRetry={() => void claim.refetch()} />
  }

  const data = claim.data!
  const closed = CLOSED_STATUSES.includes(data.status)
  const tab = claimTabOf(data.status)
  const remaining = daysUntil(data.shipDeadlineAt)
  const feedbackPhotos = attachments.data?.filter((a) => a.purpose === 'ORG_FEEDBACK') ?? []
  const shippingProofs = attachments.data?.filter((a) => a.purpose === 'SHIPPING_PROOF') ?? []
  const shipped = data.status !== 'CLAIMED' && data.trackingNumber != null
  const nickname = profile.data?.displayName

  /**
   * 取消認領（SPEC 11.2、11.4）：資料在確認當下已經改回可認領。先播「緞帶解開、禮物收回」，
   * 這段時間不重新抓這筆認領（否則版面會在動畫中途被重繪）；播完回到詳細頁，標籤彈回、
   * 認領按鈕浮上，並顯示紅色提示。
   */
  async function cancelled() {
    const serial = startNav()
    if (root.current) await playCancelOut(root.current)
    if (!isCurrentNav(serial)) return
    clearEffects()
    // 願望已經回到「可認領」：願望詳細頁、願望牆、我的認領都要重抓。先等願望詳細頁抓完再換頁，
    // 否則會先顯示快取裡的「已認領」再閃成「可認領」；這筆認領本身等換頁之後才重抓，
    // 否則動畫剛播完就先被重繪成已取消的樣子
    void queryClient.invalidateQueries({ queryKey: ['wishes'] })
    void queryClient.invalidateQueries({ queryKey: ['claims'] })
    await queryClient.invalidateQueries({ queryKey: ['wish', data.wishId] })
    if (!isCurrentNav(serial)) return
    markSpecialNavigation()
    navigate(`/wishes/${data.wishId}`, { state: { cancelled: true } })
    toast.show('已取消認領，這個願望已經回到願望牆', 'danger')
    refreshAll()
  }

  return (
    <div data-mine="true" ref={root}>
      <nav className="crumbs" aria-label="麵包屑">
        <Link to="/me/claims">我的認領</Link>
        <ChevronRight className="icon" size={14} aria-hidden />
        <span aria-current="page">{data.wishTitle}</span>
      </nav>

      {wish.data ? (
        <WishFeature
          wish={wish.data}
          statusKey={tab.key}
          statusLabel={tab.label}
          footer={<ProgressSteps claim={data} />}
        />
      ) : (
        // 公開的願望資料還沒載入（或已不可見）時，先用認領本身的資料撐住版面
        <article className="wish" data-status={tab.key}>
          <span className="status-tab status-tab--lg">{tab.label}</span>
          <section className="letter">
            <div className="letter__head"><h1 className="letter__title">{data.wishTitle}</h1></div>
            <p className="gift-note">給 {data.childAlias}・{data.ageRangeLabel}・{data.organizationName}</p>
          </section>
          <aside className="info">
            <p className="gift gift--lg"><Gift className="icon" aria-hidden /><span>{data.wishTitle}</span></p>
            <div className="info__spacer" />
            <ProgressSteps claim={data} />
          </aside>
        </article>
      )}

      <div className="task">
        <div className="ribbon" aria-hidden>
          <span className="ribbon__line" />
          <span className="ribbon__bow"><Gift className="icon" /></span>
          <span className="ribbon__line ribbon__line--r" />
        </div>
        <h2 className="task__thanks">
          謝謝你成為 <span className="grad-text">{nickname ?? '孩子'}</span> 的聖誕老人
        </h2>
        <p className="task__sub">接下來照著下面三步，把禮物送到孩子身邊。</p>

        <DeadlineBar claim={data} remaining={remaining} />

        {data.releaseReason && closed && data.status !== 'COMPLETED' && (
          <div className="notice notice--warn" style={{ marginBottom: 24 }}>
            這筆認領已結束：{data.releaseReason}
          </div>
        )}

        <div className="task__grid">
          <div className="task__main">
            {/* 放在「回報寄送」之前：要先知道寄去哪，才談得上回報寄了沒 */}
            <AddressPanel claim={data} done={data.status !== 'CLAIMED'} />

            {data.status === 'CLAIMED' && (
              <StepPanel no={2} title="寄出後，回報寄送資訊">
                <ShipForm claimId={id} onDone={refreshAll} />
              </StepPanel>
            )}

            {/* 待寄送時物流業者／追蹤碼／照片在「回報寄送」表單一次填完；一旦寄出，
                改用這個合併後的唯讀摘要顯示，不要拆成兩個區塊各顯示一半 */}
            {shipped && (
              <ShipSummary claim={data} proofs={shippingProofs} />
            )}

            {feedbackPhotos.length > 0 && (
              <section className="panel">
                <div className="panel__head"><h2 className="panel__title">機構的回饋</h2></div>
                <p style={{ margin: '0 0 12px', fontSize: 14, color: 'var(--fg-muted)' }}>
                  這些照片只有你和機構看得到，網址會在數分鐘後失效。
                </p>
                <ProofGrid photos={feedbackPhotos} emptyHint="" />
              </section>
            )}

            <StepPanel no={3} title="有問題就問機構">
              <Chat claimId={id} closed={closed} counterpart="機構" />
            </StepPanel>
          </div>

          <div className="task__side">
            <section className="panel" aria-labelledby="miniOrgName">
              <OrgPhoto mini />
              <div className="org__eyebrow">這份願望來自</div>
              <h2 className="mini-org__name" id="miniOrgName">{data.organizationName}</h2>
              <p className="mini-org__intro" />
              <OrgSiteLink />
            </section>
            {data.status === 'CLAIMED' && <CancelPanel claimId={id} onCancelled={cancelled} />}
          </div>
        </div>
      </div>
    </div>
  )
}
