import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AttachmentGallery, PreviewImage } from '../../components/console/ImageViewer'
import { ConfirmDialog } from '../../components/console/ConfirmDialog'
import { ConversationForm, ConversationLog } from '../../components/console/ConversationLog'
import { Drawer } from '../../components/console/Drawer'
import {
  DCard, DrawerTabs, DStatus, DSteps, Kv, type DrawerIcon, type StepState,
} from '../../components/console/DrawerParts'
import { ImagePlus } from 'lucide-react'
import { useToast } from '../../components/ui/toastContext'
import { Dialog } from '../../components/ui/Dialog'
import { ApiError, api } from '../../lib/api'
import { formatDate, formatDateTime, formatMonthDay, formatPlainDate, daysUntil, todayPlainDate } from '../../lib/format'
import { burst, center, reducedMotion } from '../../lib/motion'
import type { AttachmentView, ClaimOrgView, WishConversation, WishOrgView } from '../../lib/types'
import { uploadImage, validateImage } from '../../lib/upload'
import { actionsOf, chipLabelOf, STAGE_INFO } from '../../lib/wishFlow'
import { avatarSrc } from '../../lib/avatar'
import { useInvalidateOrg, useOrgWish } from './orgQueries'
import { useOrgContext } from './orgContext'

const RECALL_REASONS = [
  { value: 'OVERDUE', label: '逾期未寄送' },
  { value: 'UNREACHABLE', label: '認領者聯繫不上' },
  { value: 'CANNOT_COMPLETE', label: '認領者表示無法完成' },
  { value: 'OTHER', label: '其他' },
] as const
type RecallReason = typeof RECALL_REASONS[number]['value']

const errorText = (error: unknown) => error instanceof ApiError ? error.detail : error instanceof Error ? error.message : '發生錯誤，請稍後再試'

/**
 * 願望詳細（機構抽屜，設計稿 15.4）：分頁「願望與寄送」「與{認領者}的對話」，
 * 狀態卡（顏色＝狀態，同時是整個抽屜的強調色）→ 進度 → 寄送資訊 → 孩子的願望，
 * 底部動作按鈕依狀態而定（說明文字已放在狀態卡）。確認收到、完成、收回、刪除都有確認步驟。
 */
export function OrgWishDrawer({ wishId, onClose, onEdit }: {
  wishId: string | null
  onClose: () => void
  onEdit: (wish: WishOrgView) => void
}) {
  const detail = useOrgWish(wishId)
  const { organization } = useOrgContext()
  const invalidate = useInvalidateOrg()
  const toast = useToast()
  const navigate = useNavigate()

  const [tab, setTab] = useState<'info' | 'chat'>('info')
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [recalling, setRecalling] = useState(false)
  const [beginningEdit, setBeginningEdit] = useState(false)
  // 抽屜關閉動畫期間內容要還在（詳細資料不能立刻清掉）
  const [shown, setShown] = useState(detail.data)
  useEffect(() => { if (detail.data) setShown(detail.data) }, [detail.data])
  useEffect(() => {
    if (wishId) { setTab('info'); setConfirming(false) }
  }, [wishId])

  const data = detail.data ?? shown
  const wish = data?.wish
  const claim = data?.activeClaim
  const stage = wish?.stage
  const info = stage ? STAGE_INFO[stage] : null
  const actions = stage && wish ? actionsOf(stage, wish) : null

  const conversation = useQuery({
    queryKey: ['org-conversation', wishId],
    queryFn: () => api.get<WishConversation>(`/api/organizations/me/wishes/${wishId}/conversation`),
    enabled: Boolean(wishId),
  })
  const hasChat = Boolean(claim) || (conversation.data?.messages.length ?? 0) > 0

  // 切到對話分頁才把未讀歸零
  useEffect(() => {
    if (tab !== 'chat' || !wishId || !(conversation.data?.unread)) return
    api.post(`/api/organizations/me/wishes/${wishId}/conversation/read`)
      .then(() => invalidate())
      .catch(() => undefined)
    // 只在切到對話分頁、且有未讀時觸發
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, conversation.data?.unread, wishId])

  const claimAttachments = useQuery({
    queryKey: ['claim', claim?.id, 'attachments'],
    queryFn: () => api.get<AttachmentView[]>(`/api/claims/${claim!.id}/attachments`),
    enabled: Boolean(claim?.id),
  })

  /** 動作成功後：重新載入全部、讓抽屜的狀態卡輕輕縮一下、該列閃一下（設計稿 afterOrgChange）。 */
  async function afterChange(message: string, close = false) {
    await invalidate()
    toast.show(message)
    if (close) { onClose(); return }
    if (!reducedMotion()) {
      document.querySelector('.drawer .dstatus')?.animate(
        [{ transform: 'scale(.98)' }, { transform: 'none' }], { duration: 320, easing: 'cubic-bezier(.22,.8,.3,1)' })
    }
    if (wishId && !reducedMotion()) {
      document.querySelector(`.org-row[data-id="${wishId}"]`)?.animate(
        [{ backgroundColor: 'rgba(110,231,183,.18)' }, { backgroundColor: 'rgba(110,231,183,0)' }],
        { duration: 1200, easing: 'ease-out' })
    }
  }

  const submit = useMutation({
    mutationFn: () => api.post(`/api/wishes/${wishId}/submit`),
    onSuccess: () => afterChange(`「${wish?.title}」已提交審核，通過後會自動上架`),
    onError: (error) => toast.show(errorText(error), 'danger'),
  })
  const withdraw = useMutation({
    mutationFn: () => api.post(`/api/wishes/${wishId}/withdraw`),
    onSuccess: () => afterChange(`「${wish?.title}」已撤回，改為草稿`),
    onError: (error) => toast.show(errorText(error), 'danger'),
  })
  // 修改已上架的願望：先原子地從願望牆撤下，成功後直接打開編輯表單
  const beginEdit = useMutation({
    mutationFn: () => api.post<WishOrgView>(`/api/wishes/${wishId}/begin-edit`),
    onSuccess: async (updated) => {
      setBeginningEdit(false)
      await invalidate()
      toast.show(`「${updated.title}」已從願望牆撤下，修改後請重新提交審核`)
      onEdit(updated)
    },
    onError: async (error) => {
      // 剛好被認領等競態：關掉確認框、重新整理，讓畫面反映最新狀態
      await invalidate()
      toast.show(errorText(error), 'danger')
    },
  })
  // 下架修改後內容沒改：直接恢復上架，不必重審
  const restore = useMutation({
    mutationFn: () => api.post(`/api/wishes/${wishId}/restore`),
    onSuccess: () => afterChange(`「${wish?.title}」已恢復上架`),
    onError: (error) => toast.show(errorText(error), 'danger'),
  })
  const remove = useMutation({
    mutationFn: () => api.delete(`/api/wishes/${wishId}`),
    onSuccess: async () => { setDeleting(false); await afterChange('草稿已刪除', true) },
  })

  const pending = organization.status !== 'APPROVED'

  if (!wish || !info || !actions) {
    return (
      <Drawer open={false} onClose={onClose} labelledBy="owTitle" title="">
        <div className="drawer__body" />
      </Drawer>
    )
  }

  const donor = claim?.donorName ?? '認領者'
  const chatLabel = claim ? `與${donor}的對話` : '通知'
  const tabs = hasChat
    ? [
      { key: 'info', label: claim ? '願望與寄送' : '願望內容' },
      { key: 'chat', label: chatLabel, unread: conversation.data?.unread },
    ]
    : []
  const active = hasChat ? tab : 'info'

  return (
    <>
      <Drawer
        open={Boolean(wishId)}
        onClose={onClose}
        tone={info.tone}
        labelledBy="owTitle"
        title={wish.title}
        chip={<span className="st-chip" data-s={info.chip}>{chipLabelOf(wish)}</span>}
        tabs={<DrawerTabs tabs={tabs} active={active} onChange={(key) => setTab(key as 'info' | 'chat')} />}
        foot={active === 'chat' ? null : (
          <div className="drawer__foot">
            {actions.remove && (
              <button type="button" className="btn btn--danger" onClick={() => setDeleting(true)}>刪除</button>
            )}
            {actions.restore && (
              <button type="button" className="btn" disabled={restore.isPending}
                onClick={() => restore.mutate()}>不修改了，恢復上架</button>
            )}
            {actions.edit && <button type="button" className="btn" onClick={() => onEdit(wish)}>編輯</button>}
            {actions.beginEdit && (
              <button type="button" className="btn btn--tone" onClick={() => setBeginningEdit(true)}>修改並重新送審</button>
            )}
            {actions.submit && (
              <button type="button" className="btn btn--primary" disabled={pending || submit.isPending}
                onClick={() => submit.mutate()}>提交審核</button>
            )}
            {actions.withdraw && (
              <button type="button" className="btn" disabled={withdraw.isPending}
                onClick={() => withdraw.mutate()}>撤回改為草稿</button>
            )}
            {actions.recall && (
              <button type="button" className="btn btn--tone" onClick={() => setRecalling(true)}>收回這個願望</button>
            )}
            {actions.confirm && claim && (
              <ConfirmButtons
                confirming={confirming}
                onReport={() => {
                  onClose()
                  navigate('/org/chat', { state: { prefill: `關於「${wish.title}」（${wish.childAlias}）：收到的禮物有問題，` } })
                }}
                onStart={() => setConfirming(true)}
                onCancel={() => setConfirming(false)}
              />
            )}
          </div>
        )}
      >
        <div className="drawer__body">
          <div className="dpanel" hidden={active !== 'info'}>
            <WishInfo
              wish={wish}
              claim={claim}
              past={data?.pastClaims ?? []}
              attachments={claimAttachments.data ?? []}
              orgPending={pending}
              confirming={confirming}
              onConfirmed={async () => {
                setConfirming(false)
                await afterChange(`已確認收到「${wish.title}」，系統已通知認領者`)
              }}
              onAttachmentsChanged={() => void claimAttachments.refetch()}
            />
          </div>
          {hasChat && (
            <div className="dpanel dpanel--chat" hidden={active !== 'chat'}>
              <ConversationLog messages={conversation.data?.messages ?? []} counterpart={donor}
                className="" label="與認領者的對話" />
              {conversation.data?.canSend && (
                <ConversationSender wishId={wish.id} donor={donor} />
              )}
            </div>
          )}
        </div>
      </Drawer>

      <ConfirmDialog
        open={deleting}
        title="要刪除這個草稿嗎？"
        body={`刪除後「${wish.title}」無法復原。`}
        okText="刪除草稿"
        busy={remove.isPending}
        error={remove.isError ? errorText(remove.error) : undefined}
        onOk={() => remove.mutate()}
        onClose={() => setDeleting(false)}
      />
      <ConfirmDialog
        open={beginningEdit}
        tone="primary"
        title="要修改並重新送審嗎？"
        body={`按下後，「${wish.title}」會立刻從願望牆撤下，修改完成並提交審核、管理員核准後才會重新上架。審核期間不會有人看到或認領這個願望。${wish.frozen ? '它目前被管理員凍結，重新送審會取代凍結。' : ''}`}
        okText="撤下並開始修改"
        busy={beginEdit.isPending}
        error={beginEdit.isError ? errorText(beginEdit.error) : undefined}
        onOk={() => beginEdit.mutate()}
        onClose={() => setBeginningEdit(false)}
      />
      <RecallDialog
        open={recalling}
        wishTitle={wish.title}
        claim={claim}
        onClose={() => setRecalling(false)}
        onDone={async (reason) => {
          setRecalling(false)
          await afterChange(`已收回「${wish.title}」（${reason}），願望已重新上架`)
        }}
      />
    </>
  )
}

function ConfirmButtons({ confirming, onReport, onStart, onCancel }: {
  confirming: boolean
  onReport: () => void
  onStart: () => void
  onCancel: () => void
}) {
  // 「確認收到，完成願望」在 WishInfo 的確認區塊裡送出（它有欄位要檢查）；這裡只負責展開與取消
  if (confirming) {
    return (
      <>
        <button key="cancel" type="button" className="btn" onClick={onCancel}>取消</button>
        <button key="confirm" type="submit" form="owConfirmForm" className="btn btn--tone-solid">確認收到，完成願望</button>
      </>
    )
  }
  return (
    <>
      <button type="button" className="btn" onClick={onReport}>回報異常</button>
      <button key="start" type="button" className="btn btn--tone-solid" onClick={onStart}>確認收到並完成</button>
    </>
  )
}

function ConversationSender({ wishId, donor }: { wishId: string; donor: string }) {
  const invalidate = useInvalidateOrg()
  const send = useMutation({
    mutationFn: (body: string) => api.post(`/api/organizations/me/wishes/${wishId}/conversation`, { body }),
    onSuccess: () => invalidate(),
  })
  return (
    <ConversationForm placeholder={`傳訊息給 ${donor}…`} busy={send.isPending}
      error={send.isError ? errorText(send.error) : null}
      onSend={async (text) => { await send.mutateAsync(text) }} />
  )
}

/** 願望與寄送：狀態卡 → 確認收到區塊 → 進度 → 寄送資訊 → 孩子的願望 →（有的話）過往認領紀錄。 */
function WishInfo({ wish, claim, past, attachments, orgPending, confirming, onConfirmed, onAttachmentsChanged }: {
  wish: WishOrgView
  claim?: ClaimOrgView
  past: { claimId: string; status: string; donorName?: string; claimedAt: string; endedAt?: string; reason?: string }[]
  attachments: AttachmentView[]
  orgPending: boolean
  confirming: boolean
  onConfirmed: () => void | Promise<void>
  onAttachmentsChanged: () => void
}) {
  const stage = wish.stage
  const summary = wish.activeClaim
  const donor = claim?.donorName ?? summary?.donorName ?? '認領者'
  const shippingPhotos = attachments.filter((item) => item.purpose === 'SHIPPING_PROOF')
  const proofs = attachments.filter((item) => item.purpose === 'ORG_FEEDBACK')

  return (
    <>
      <StatusCard wish={wish} donor={donor} orgPending={orgPending} />

      {confirming && claim && (
        <ConfirmReceivedBox wish={wish} claim={claim} proofs={proofs}
          onConfirmed={onConfirmed} onProofsChanged={onAttachmentsChanged} />
      )}

      {summary && <ClaimSteps summary={summary} done={stage === 'DONE'} />}

      {summary && (
        <DCard icon="truck" title="寄送資訊" className="dcard--key">
          <div className="dperson">
            <div>
              <strong>{donor}</strong>
              <span>{formatDateTime(summary.claimedAt)} 認領</span>
            </div>
          </div>
          {claim?.shippedAt ? (
            <>
              <div className="dgift">
                <strong>{claim.giftDescription ?? '—'}</strong>
                <span>{claim.giftQuantity != null ? `${claim.giftQuantity} 份` : ''}</span>
              </div>
              <Kv rows={[
                { label: '寄出日期', value: formatPlainDate(claim.shippedOn) },
                { label: '寄件照片', value: `${shippingPhotos.length} 張` },
                { label: '物流業者', value: claim.trackingCarrier ?? '—' },
                { label: '追蹤碼', value: claim.trackingNumber ?? '—' },
              ]} />
              <Thumbs photos={shippingPhotos} label="寄件照片" />
            </>
          ) : (
            <p className="dempty">還沒寄出，認領者回報寄送後會出現禮物與物流資訊。</p>
          )}
        </DCard>
      )}

      {stage === 'DONE' && proofs.length > 0 && (
        <DCard icon="check" title="收件證明">
          <Thumbs photos={proofs} label="收件證明" />
        </DCard>
      )}

      <DCard icon="gift" title="孩子的願望">
        <div className="dwish">
          <img className="avatar" src={avatarSrc(wish.avatar)} alt="" width={56} height={56} />
          <div><strong>{wish.childAlias || '尚未填寫暱稱'}</strong><span>{wish.ageRangeLabel ?? ''}</span></div>
        </div>
        {wish.description && <p className="dquote">{wish.description}</p>}
        <div className="dletter">
          <PreviewImage src={wish.letterPhotos[0]?.url} alt="孩子的手寫信件">【孩子的手寫信件照片】</PreviewImage>
        </div>
      </DCard>

      {past.length > 0 && (
        <DCard icon="user" title="過往認領紀錄">
          <Kv rows={past.map((item) => ({
            label: `${formatDate(item.claimedAt)}・${item.donorName ?? '認領者'}`,
            value: `${item.status === 'CANCELLED' ? '認領者取消' : '機構收回'}${item.reason ? `（${item.reason}）` : ''}`,
            full: true,
          }))} />
        </DCard>
      )}
    </>
  )
}

function Thumbs({ photos, label }: { photos: AttachmentView[]; label: string }) {
  return <AttachmentGallery files={photos} altPrefix={label} />
}

/** 狀態卡：現在怎樣、下一步要做什麼（設計稿 orgWishStatus）。 */
function StatusCard({ wish, donor, orgPending }: { wish: WishOrgView; donor: string; orgPending: boolean }) {
  const summary = wish.activeClaim
  const info = STAGE_INFO[wish.stage]
  let icon: DrawerIcon
  let title: React.ReactNode
  let desc: React.ReactNode

  switch (wish.stage) {
    case 'DRAFT':
      icon = 'pencil'
      if (wish.takenDown) {
        // 曾經上架過：機構正在修改，或重審被退回。願望目前不在願望牆上
        title = wish.reviewNote ? '重新審核被退回，目前已下架' : '下架修改中，還沒重新上架'
        desc = orgPending
          ? '機構資料審核通過後，才能再次提交這個願望。'
          : '修改後按「提交審核」，管理員核准才會再次出現在願望牆。內容沒有要改的話，可以按「不修改了，恢復上架」。'
      } else {
        title = '草稿，還沒提交'
        desc = orgPending
          ? '機構資料審核通過後，才能提交這個願望。'
          : '確認內容後按「提交審核」，管理員核准後才會出現在願望牆。'
      }
      if (wish.reviewNote) desc = <>{desc}<br />上次被退回的原因：{wish.reviewNote}</>
      break
    case 'PENDING_REVIEW':
      icon = 'search'
      title = wish.takenDown || wish.publishedAt ? '修改後重新審核中' : '管理員審核中'
      desc = '通過後會自動出現在願望牆；需要再修改請先撤回改為草稿。'
      break
    case 'OPEN':
      icon = 'spark'
      title = '在願望牆上，等待認領'
      desc = wish.frozen
        ? '管理員暫時凍結了這個願望，目前不會顯示在願望牆。按「修改並重新送審」修正後，重新送審會取代凍結。'
        : '需要調整內容請按「修改並重新送審」：願望會先從願望牆撤下，核准後才重新上架。'
      break
    case 'DONE':
      icon = 'check'
      title = '已完成'
      desc = `${formatMonthDay(summary?.receivedOn ?? summary?.receivedAt)} 收到禮物，${formatDateTime(summary?.completedAt)} 在網站確認，系統已通知認領者。`
      break
    case 'SHIPPED':
      icon = 'truck'
      title = `${donor} 已寄出，等你確認收到`
      desc = '收到後請先拆封檢查，再按下方「確認收到並完成」。'
      break
    case 'CLAIMED_OVERDUE': {
      icon = 'alert'
      const late = Math.max(1, -(daysUntil(summary?.shipDeadlineAt) ?? 0))
      title = `已超過寄出期限 ${late} 天`
      desc = `${donor} 還沒回報寄送。可以在「對話」提醒對方，或收回願望重新上架。願望內容已鎖定、不能修改。`
      break
    }
    case 'CLAIMED_WAITING':
      icon = 'clock'
      title = `等 ${donor} 寄出禮物`
      desc = `寄出期限 ${formatDate(summary?.shipDeadlineAt)}（還有 ${daysUntil(summary?.shipDeadlineAt)} 天）。願望已被認領，內容已鎖定、不能修改；認領者會照目前的內容準備禮物。有問題請在「對話」與認領者溝通。`
      break
    default:
      icon = 'alert'
      title = '資料異常'
      desc = '這個願望顯示已被認領，卻找不到進行中的認領；請與平台管理員聯繫。'
  }
  return <DStatus tone={info.tone} icon={icon} title={title} desc={desc} />
}

/** 進度：認領 → 寄出 → 收到，各附日期。 */
function ClaimSteps({ summary, done }: {
  summary: NonNullable<WishOrgView['activeClaim']>
  done: boolean
}) {
  const shipped = Boolean(summary.shippedAt)
  const late = summary.overdue
  const steps: { label: string; sub: string; state: StepState }[] = [
    { label: '認領', sub: formatMonthDay(summary.claimedAt), state: 'done' },
    {
      label: '寄出',
      sub: shipped ? formatMonthDay(summary.shippedAt) : late ? '已逾期' : `期限 ${formatMonthDay(summary.shipDeadlineAt)}`,
      state: shipped ? 'done' : late ? 'late' : 'now',
    },
    {
      label: '收到',
      sub: done ? formatMonthDay(summary.receivedOn ?? summary.receivedAt) : shipped ? '等你確認' : '—',
      state: done ? 'done' : shipped ? 'now' : 'todo',
    },
  ]
  return <DSteps steps={steps} complete={done} />
}

/**
 * 確認收到並完成的確認區塊（設計稿 owConfirm）：實際收到日期（預設今天）＋「已拆封檢查」。
 * 另外要有至少一張機構上傳的收件證明照片（經使用者確認保留且必填，設計稿沒有這一項）。
 * 沒完成時整個區塊輕晃並提示。
 */
function ConfirmReceivedBox({ wish, claim, proofs, onConfirmed, onProofsChanged }: {
  wish: WishOrgView
  claim: ClaimOrgView
  proofs: AttachmentView[]
  onConfirmed: () => void | Promise<void>
  onProofsChanged: () => void
}) {
  const box = useRef<HTMLElement>(null)
  const [date, setDate] = useState(todayPlainDate())
  const [checked, setChecked] = useState(false)
  const [error, setError] = useState('')
  const [uploading, setUploading] = useState(false)
  const toast = useToast()

  useEffect(() => {
    if (reducedMotion()) return
    box.current?.animate([{ opacity: 0, transform: 'translateY(10px)' }, { opacity: 1, transform: 'none' }],
      { duration: 320, easing: 'cubic-bezier(.22,.8,.3,1)' })
    box.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [])

  const complete = useMutation({
    mutationFn: () => api.post(`/api/organizations/me/claims/${claim.id}/complete`,
      { receivedOn: date, inspected: true }),
    onSuccess: () => onConfirmed(),
    onError: (e) => setError(errorText(e)),
  })

  function shake() {
    if (!reducedMotion()) {
      box.current?.animate([{ transform: 'translateX(0)' }, { transform: 'translateX(-6px)' },
        { transform: 'translateX(6px)' }, { transform: 'none' }], { duration: 300 })
    }
  }

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const message = !date ? '請填寫實際收到日期。'
      : !checked ? '請先完成拆封檢查再確認。'
        : proofs.length === 0 ? '請先上傳至少一張收件證明照片。' : ''
    setError(message)
    if (message) { shake(); return }
    const button = document.querySelector<HTMLElement>('button[form="owConfirmForm"]')
    if (button) burst(...center(button), 18, 110)
    complete.mutate()
  }

  async function addProof(file: File | undefined) {
    if (!file) return
    const problem = validateImage(file)
    if (problem) { setError(problem); return }
    setUploading(true)
    try {
      await uploadImage('ORG_FEEDBACK', claim.id, file)
      setError('')
      onProofsChanged()
    } catch (e) {
      toast.show(errorText(e), 'danger')
    } finally {
      setUploading(false)
    }
  }

  return (
    <section className="confirm-box" ref={box} aria-labelledby="owConfirmTitle">
      <form id="owConfirmForm" onSubmit={submit} noValidate style={{ display: 'contents' }}>
        <h3 id="owConfirmTitle">確認收到「{wish.title}」</h3>
        <div className="field">
          <label className="field__label" htmlFor="owDate">實際收到日期</label>
          <span className="field__hint">實際收受日期，會和按下確認的時間分開記錄</span>
          <input className="input" type="date" id="owDate" value={date} max={todayPlainDate()}
            onChange={(event) => setDate(event.target.value)} />
        </div>
        <div className="field">
          <span className="field__label">收件證明照片<span className="req" aria-hidden>*</span></span>
          <span className="field__hint">至少一張，讓認領者知道禮物平安送達</span>
          <div className="upload">
            <label className="upload__btn">
              <ImagePlus className="icon" aria-hidden />
              {uploading ? '上傳中…' : '新增照片'}
              <input type="file" accept="image/*" disabled={uploading}
                aria-label="收件證明照片"
                onChange={(event) => { const f = event.target.files?.[0]; event.target.value = ''; void addProof(f) }} />
            </label>
            <span className="upload__list">{proofs.length > 0 && `已上傳 ${proofs.length} 張`}</span>
          </div>
          {proofs.length > 0 && <Thumbs photos={proofs} label="收件證明" />}
        </div>
        <label className="check-row">
          <input type="checkbox" checked={checked} onChange={(event) => setChecked(event.target.checked)} />
          <span>已拆封檢查，沒有禁止物品或可疑裝置</span>
        </label>
        <span className="field__error" role="alert">{error}</span>
      </form>
    </section>
  )
}

/** 收回願望（設計稿 recallDialog）：原因四選一，選「其他」可補寫；收回後立刻重新上架。 */
function RecallDialog({ open, wishTitle, claim, onClose, onDone }: {
  open: boolean
  wishTitle: string
  claim?: ClaimOrgView
  onClose: () => void
  onDone: (reasonLabel: string) => void | Promise<void>
}) {
  const [reason, setReason] = useState<RecallReason>('OVERDUE')
  const [detail, setDetail] = useState('')
  const recall = useMutation({
    mutationFn: () => api.post(`/api/organizations/me/claims/${claim!.id}/release`,
      { reason, detail: reason === 'OTHER' && detail.trim() ? detail.trim() : undefined }),
    onSuccess: () => onDone(RECALL_REASONS.find((item) => item.value === reason)!.label),
  })
  function close() {
    setReason('OVERDUE')
    setDetail('')
    recall.reset()
    onClose()
  }

  return (
    <Dialog open={open} onClose={close} labelledBy="recallTitle" dismissible={!recall.isPending}>
      <h2 id="recallTitle">確定要收回這個願望嗎？</h2>
      <p>收回後，「{wishTitle}」會立刻重新上架，原本的認領者會收到 Email 通知，並在認領頁看到收回原因，不能再回報寄送。</p>
      <fieldset className="recall-reasons">
        <legend className="field__label">收回原因</legend>
        {RECALL_REASONS.map((item) => (
          <label key={item.value} className="check-row">
            <input type="radio" name="recallReason" checked={reason === item.value}
              onChange={() => setReason(item.value)} />
            <span>{item.label}</span>
          </label>
        ))}
      </fieldset>
      {reason === 'OTHER' && (
        <div className="field" style={{ marginTop: 12 }}>
          <input className="input" maxLength={200} value={detail} placeholder="補充說明（選填）"
            aria-label="補充說明" onChange={(event) => setDetail(event.target.value)} />
        </div>
      )}
      {recall.isError && <p className="field__error" role="alert">{errorText(recall.error)}</p>}
      <div className="dialog__actions">
        <button type="button" className="btn" onClick={close} disabled={recall.isPending}>再想想</button>
        <button type="button" className="btn btn--danger-solid" disabled={recall.isPending}
          onClick={() => recall.mutate()}>{recall.isPending ? '處理中…' : '收回並重新上架'}</button>
      </div>
    </Dialog>
  )
}
