import { ChevronRight, Search } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Drawer } from '../../components/console/Drawer'
import { AttachmentGallery } from '../../components/console/ImageViewer'
import {
  DCard, DrawerTabs, DStatus, DSteps, Kv, type StepState,
} from '../../components/console/DrawerParts'
import { LazyBox } from '../../components/console/LazyBox'
import { ReasonDialog } from '../../components/console/ReasonDialog'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { useToast } from '../../components/ui/toastContext'
import { ApiError, api } from '../../lib/api'
import { avatarSrc } from '../../lib/avatar'
import { pageTitle } from '../../lib/brand'
import { daysUntil, formatDate, formatDateTime, formatMonthDay, formatPlainDate } from '../../lib/format'
import { burst, center, EASE_OUT, reducedMotion, rise } from '../../lib/motion'
import type {
  AdminMessageView, AdminWishDetail, AdminWishFilter, AdminWishListItem, AttachmentView, ClaimReceiptView,
} from '../../lib/types'
import {
  useAdminOrganizations, useAdminWish, useAdminWishCounts, useAdminWishes, useInvalidateAdmin,
} from './adminQueries'

type Chip = 'review' | 'open' | 'claimed' | 'done'

const FILTERS: { key: string; value: AdminWishFilter; label: string; count: (c: NonNullable<ReturnType<typeof useAdminWishCounts>['data']>) => number }[] = [
  { key: 'all', value: 'ALL', label: '全部', count: (c) => c.all },
  { key: 'review', value: 'REVIEW', label: '待審核', count: (c) => c.review },
  { key: 'open', value: 'OPEN', label: '可認領', count: (c) => c.open },
  { key: 'claimed', value: 'CLAIMED', label: '已認領', count: (c) => c.claimed },
  { key: 'late', value: 'LATE', label: '逾期未寄', count: (c) => c.late },
  { key: 'done', value: 'DONE', label: '已完成', count: (c) => c.done },
  { key: 'frozen', value: 'FROZEN', label: '已凍結', count: (c) => c.frozen },
]
const CHIP_LABEL: Record<Chip, string> = { review: '待審核', open: '可認領', claimed: '已認領', done: '已完成' }
const chipOf = (status: AdminWishListItem['status']): Chip =>
  status === 'PENDING_REVIEW' ? 'review' : status === 'AVAILABLE' ? 'open' : status === 'CLAIMED' ? 'claimed' : 'done'

const ICON_SNOW = (
  <svg className="icon" viewBox="0 0 24 24" aria-hidden>
    <path d="M2 12h20" /><path d="M12 2v20" /><path d="m20 16-4-4 4-4" /><path d="m4 8 4 4-4 4" />
    <path d="m16 4-4 4-4-4" /><path d="m8 20 4-4 4 4" />
  </svg>
)

/**
 * 管理員「願望」（設計稿 16.3）：願望與它的認領合成一份清單；篩選七項（各帶數量）＋搜尋＋年度，
 * 點一列開抽屜。核准／退回審核中的願望、暫時凍結與解除凍結。
 * 價格與捐贈人資料只有管理員看得到；附件、對話、捐贈收據是點擊載入並寫稽核。
 */
export function AdminWishes() {
  const [params, setParams] = useSearchParams()
  const filter = FILTERS.find((item) => item.key === params.get('filter')) ?? FILTERS[0]
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [year, setYear] = useState<number | undefined>()
  // 機構篩選放在網址（?org=），機構審核抽屜的「查看這間機構的願望」與重新整理都能帶著
  const organizationId = params.get('org') ?? undefined
  const organizations = useAdminOrganizations()
  const counts = useAdminWishCounts(organizationId)
  const wishes = useAdminWishes(filter.value, q, year, organizationId)
  const [openId, setOpenId] = useState<string | null>(null)
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    document.title = pageTitle('願望｜管理員後台')
    return () => { document.title = pageTitle() }
  }, [])

  // 搜尋輸入停 300ms 才打 API
  useEffect(() => {
    const timer = window.setTimeout(() => setQ(search.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [search])

  function chooseOrganization(id: string) {
    const next = new URLSearchParams(params)
    if (id) next.set('org', id); else next.delete('org')
    setParams(next, { replace: true })
  }

  function chooseFilter(key: string) {
    const next = new URLSearchParams(params)
    next.set('filter', key)
    setParams(next, { replace: true })
    if (!reducedMotion()) {
      window.requestAnimationFrame(() => rise(
        [...(listRef.current?.querySelectorAll('.org-row') ?? [])],
        { delay: 0, stagger: 30, dist: 8, dur: 320 }))
    }
  }

  const years = yearOptions()

  return (
    <section className="org-tab" aria-labelledby="adWishTitle">
      <div className="org-head">
        <h1 id="adWishTitle">願望</h1>
        <div className="ad-tools">
          <select className="input ad-org" aria-label="機構" value={organizationId ?? ''}
            onChange={(event) => chooseOrganization(event.target.value)}>
            <option value="">全部機構</option>
            {[...(organizations.data?.content ?? [])]
              .filter((org) => org.status === 'APPROVED' || org.status === 'SUSPENDED' || org.id === organizationId)
              .sort((a, b) => a.name.localeCompare(b.name, 'zh-Hant'))
              .map((org) => <option key={org.id} value={org.id}>{org.name}</option>)}
          </select>
          <select className="input ad-year" aria-label="年度" value={year ?? ''}
            onChange={(event) => setYear(event.target.value ? Number(event.target.value) : undefined)}>
            <option value="">全部年度</option>
            {years.map((item) => <option key={item} value={item}>{item} 年</option>)}
          </select>
          <label className="ad-search">
            <Search className="icon" aria-hidden />
            <span className="sr-only">搜尋願望</span>
            <input className="input" type="search" placeholder="搜尋禮物、孩子暱稱或機構"
              value={search} onChange={(event) => setSearch(event.target.value)} />
          </label>
        </div>
      </div>

      <div className="org-filters" role="tablist" aria-label="依狀態篩選">
        {FILTERS.map((item) => (
          <button key={item.key} type="button" role="tab" className="org-filter"
            aria-selected={item.key === filter.key} onClick={() => chooseFilter(item.key)}>
            {item.label}<span>{counts.data ? item.count(counts.data) : 0}</span>
          </button>
        ))}
      </div>

      {wishes.isLoading && <Spinner label="載入願望" />}
      {wishes.isError && <ErrorNotice error={wishes.error} onRetry={() => void wishes.refetch()} />}
      {wishes.data && (
        <div className="org-list" role="list" ref={listRef}>
          {wishes.data.content.length === 0 ? <p className="org-empty">沒有符合的願望。</p>
            : wishes.data.content.map((wish) => {
              const chip = chipOf(wish.status)
              return (
                <button key={wish.id} type="button" className="org-row" data-s={chip} role="listitem"
                  aria-label={`${CHIP_LABEL[chip]}，${wish.title}，${wish.organizationName}`}
                  onClick={() => setOpenId(wish.id)}>
                  <span className="st-chip" data-s={chip}>{CHIP_LABEL[chip]}</span>
                  <span className="org-row__title">{wish.title}
                    <span className="org-row__sub">{wish.organizationName}</span>
                  </span>
                  <span className="org-row__who">{[wish.childAlias, wish.ageRangeLabel].filter(Boolean).join('・')}</span>
                  <span className="org-row__badges">
                    {wish.frozen && <span className="badge badge--frozen">{ICON_SNOW}已凍結</span>}
                    {wish.late && <span className="badge badge--late">逾期未寄</span>}
                  </span>
                  <ChevronRight className="icon org-row__go" aria-hidden />
                </button>
              )
            })}
        </div>
      )}

      <WishDrawer wishId={openId} onClose={() => setOpenId(null)} />
    </section>
  )
}

/** 從今年往回 5 年的選項（願望以建立年度歸檔）。 */
function yearOptions(): number[] {
  const now = new Date().getFullYear()
  return Array.from({ length: 6 }, (_, i) => now - i)
}

function WishDrawer({ wishId, onClose }: { wishId: string | null; onClose: () => void }) {
  const toast = useToast()
  const invalidate = useInvalidateAdmin()
  const detail = useAdminWish(wishId)
  const [tab, setTab] = useState('info')
  const [dialog, setDialog] = useState<'return' | 'freeze' | null>(null)
  const [busy, setBusy] = useState(false)

  // 換一個願望回到第一個分頁；留住最後一筆資料，關閉動畫期間內容不會消失
  const [lastId, setLastId] = useState<string | null>(null)
  if (wishId && wishId !== lastId) { setLastId(wishId); setTab('info') }
  const [shown, setShown] = useState<AdminWishDetail | null>(null)
  if (detail.data && detail.data !== shown) setShown(detail.data)
  const wish = detail.data ?? (wishId === shown?.id ? shown : null)
  const open = wishId !== null

  async function post(path: string, body: unknown, message: string, celebrate?: Element | null) {
    await api.post(`/api/admin/wishes/${wishId}/${path}`, body)
    if (celebrate && !reducedMotion()) burst(...center(celebrate), 16, 100)
    toast.show(message)
    await invalidate()
    if (path === 'freeze' && !reducedMotion()) {
      window.requestAnimationFrame(() => document.querySelector('dialog.drawer[open] .dstatus')?.animate(
        [{ transform: 'scale(.97)' }, { transform: 'none' }], { duration: 320, easing: EASE_OUT }))
    }
  }

  async function approve(button: HTMLElement) {
    if (!wish || busy) return
    setBusy(true)
    try {
      await post('approve', undefined, `「${wish.title}」已核准上架，現在會出現在願望牆`, button)
      onClose()
    } catch (e) {
      toast.show(e instanceof ApiError ? e.detail : '核准失敗，請稍後再試', 'danger')
    } finally {
      setBusy(false)
    }
  }

  async function unfreeze() {
    if (!wish || busy) return
    setBusy(true)
    try {
      await post('unfreeze', undefined, `已解除凍結，「${wish.title}」重新顯示在願望牆`)
    } catch (e) {
      toast.show(e instanceof ApiError ? e.detail : '解除失敗，請稍後再試', 'danger')
    } finally {
      setBusy(false)
    }
  }

  const frozen = Boolean(wish?.frozen)
  const chip = wish ? chipOf(wish.status) : 'open'
  const tone = !wish ? 'open' : frozen ? 'frozen' : statusTone(wish)

  const foot = !wish ? undefined : wish.status === 'PENDING_REVIEW' ? (
    <div className="drawer__foot">
      <button type="button" className="btn btn--danger" onClick={() => setDialog('return')}>退回</button>
      <button type="button" className="btn btn--tone-solid" disabled={busy}
        onClick={(event) => void approve(event.currentTarget)}>核准上架</button>
    </div>
  ) : frozen ? (
    <div className="drawer__foot">
      <button type="button" className="btn btn--freeze" disabled={busy} onClick={() => void unfreeze()}>解除凍結</button>
    </div>
  ) : (
    <div className="drawer__foot">
      <button type="button" className="btn btn--freeze" onClick={() => setDialog('freeze')}>{ICON_SNOW}暫時凍結</button>
    </div>
  )

  const claim = wish?.claim
  return (
    <>
      <Drawer open={open} onClose={onClose} tone={tone} labelledBy="adWishDrawerTitle"
        chip={wish && <span className="st-chip" data-s={chip}>{CHIP_LABEL[chip]}</span>}
        title={wish?.title ?? ''}
        tabs={claim && wish ? (
          <DrawerTabs active={tab} onChange={setTab}
            tabs={[{ key: 'info', label: '詳細資料' }, { key: 'chat', label: `與${claim.donorDisplayName}的對話` }]} />
        ) : undefined}
        foot={foot}>
        <div className="drawer__body">
          {!wish && detail.isLoading && <Spinner label="載入願望" />}
          {detail.isError && <ErrorNotice error={detail.error} onRetry={() => void detail.refetch()} />}
          {wish && tab === 'info' && <WishInfo wish={wish} />}
          {wish && claim && tab === 'chat' && (
            <LazyBox key={`chat-${claim.claimId}`} title="對話" desc="機構與認領者的站內對話，可能涉及收件細節。" button="載入對話"
              load={() => api.get<AdminMessageView[]>(`/api/admin/claims/${claim.claimId}/messages`)}>
              {(messages: AdminMessageView[]) => messages.length === 0
                ? <p className="auth-note">沒有對話。</p>
                : (
                  <div className="chat" style={{ maxHeight: 280 }}>
                    {messages.map((message) => (
                      <div key={message.id} className={`msg ${message.senderRole === 'ORGANIZATION' ? 'msg--org' : 'msg--me'}`}>
                        <span className="msg__who">{message.senderRole === 'ORGANIZATION' ? wish.organization.name : claim.donorDisplayName}</span>
                        <span className="msg__text">{message.body}</span>
                        <span className="msg__time">{formatDateTime(message.sentAt)}</span>
                      </div>
                    ))}
                  </div>
                )}
            </LazyBox>
          )}
        </div>
      </Drawer>

      {wish && (
        <>
          <ReasonDialog open={dialog === 'return'} onClose={() => setDialog(null)}
            title={`退回「${wish.title}」？`} body="願望會改回草稿，機構會在該願望收到一則新訊息與退回原因。"
            label="退回原因" hint="會原封不動給機構看，請具體說明需要修改的地方" minLength={5}
            tooShort="請寫下具體的退回原因（至少 5 個字）" okText="退回"
            onSubmit={async (reason) => {
              await post('reject', { note: reason }, `已退回「${wish.title}」，機構會收到新訊息`)
              setDialog(null)
              onClose()
            }} />
          <ReasonDialog open={dialog === 'freeze'} onClose={() => setDialog(null)} tone="freeze" rows={3}
            title="暫時凍結這個願望？"
            body={`凍結後，「${wish.title}」會立刻從願望牆與認領者的頁面消失，連同信件照片與對話都不再對外顯示；機構後台與管理員後台仍會保留，處理完可以解除凍結。`}
            label="凍結原因" hint="例如：信件照片露出孩子的真實姓名" minLength={4}
            tooShort="請寫下凍結原因" okText="凍結"
            onSubmit={async (reason) => {
              await post('freeze', { note: reason }, `已凍結「${wish.title}」，已從願望牆移除`)
              setDialog(null)
            }} />
        </>
      )}
    </>
  )
}

function statusTone(wish: AdminWishDetail): string {
  if (wish.status === 'PENDING_REVIEW') return 'review'
  if (wish.status === 'AVAILABLE') return 'open'
  if (wish.status === 'FULFILLED') return 'done'
  return wish.late ? 'late' : 'claimed'
}

/** 狀態卡：凍結優先，其次依審核與認領進度。 */
function statusCard(wish: AdminWishDetail) {
  const claim = wish.claim
  const who = claim?.donorDisplayName ?? ''
  if (wish.frozen) {
    return <DStatus tone="frozen" icon="snow" title="已凍結"
      desc={<>{formatDateTime(wish.frozen.at)}：{wish.frozen.reason}<br />目前不顯示在願望牆與認領者頁面；機構後台與管理員後台仍保留。</>} />
  }
  if (wish.status === 'PENDING_REVIEW') {
    return <DStatus tone="review" icon="search" title="等你審核"
      desc={`${wish.organization.name} 已提交。核准後立刻出現在願望牆；退回需要寫原因。`} />
  }
  if (wish.status === 'AVAILABLE') return <DStatus tone="open" icon="spark" title="在願望牆上，等待認領" />
  if (wish.status === 'FULFILLED') {
    return <DStatus tone="done" icon="check" title="已完成"
      desc={claim?.receivedOn || claim?.receivedAt ? `機構於 ${formatMonthDay(claim.receivedOn ?? claim.receivedAt)} 確認收到。` : ''} />
  }
  if (!claim) return <DStatus tone="done" icon="alert" title="資料異常" desc="這個願望顯示已被認領，卻找不到進行中的認領。" />
  if (wish.late) {
    return <DStatus tone="late" icon="alert" title="認領逾期未寄送"
      desc={`${who} 超過寄出期限（${formatDate(claim.shipDeadlineAt)}）仍未回報寄送。`} />
  }
  if (claim.shippedAt) return <DStatus tone="claimed" icon="truck" title={`${who} 已寄出，等機構確認收到`} />
  return <DStatus tone="claimed" icon="clock" title={`等 ${who} 寄出禮物`}
    desc={claim.shipDeadlineAt ? `寄出期限 ${formatDate(claim.shipDeadlineAt)}（還有 ${daysUntil(claim.shipDeadlineAt)} 天）。` : ''} />
}

function claimSteps(wish: AdminWishDetail) {
  const claim = wish.claim!
  const shipped = Boolean(claim.shippedAt)
  const done = wish.status === 'FULFILLED'
  const steps: { label: string; sub: string; state: StepState }[] = [
    { label: '認領', sub: formatMonthDay(claim.claimedAt), state: 'done' },
    { label: '寄出',
      sub: shipped ? formatMonthDay(claim.shippedAt) : wish.late ? '已逾期' : `期限 ${formatMonthDay(claim.shipDeadlineAt)}`,
      state: shipped ? 'done' : wish.late ? 'late' : 'now' },
    { label: '收到',
      sub: done ? formatMonthDay(claim.receivedOn ?? claim.receivedAt) : shipped ? '等機構確認' : '—',
      state: done ? 'done' : shipped ? 'now' : 'todo' },
  ]
  return <DSteps steps={steps} complete={done} />
}

function WishInfo({ wish }: { wish: AdminWishDetail }) {
  const claim = wish.claim
  const org = (
    <DCard icon="building" title="機構">
      <p className="dlead">{wish.organization.name}</p>
      <Kv rows={[
        { label: '承辦人', value: wish.organization.contactPerson || '—', full: true },
        { label: '行動電話', value: wish.organization.mobilePhone || '—', full: true },
        { label: '聯絡信箱', value: wish.organization.contactEmail || '—', full: true },
        { label: '收件地址', value: wish.organization.address || '—', full: true },
      ]} />
    </DCard>
  )
  return (
    <>
      {statusCard(wish)}
      {claim && wish.status !== 'PENDING_REVIEW' && claimSteps(wish)}
      {claim?.shippedAt && (
        <DCard icon="truck" title="寄送資訊" className="dcard--key">
          <div className="dgift"><strong>{claim.giftDescription}</strong><span>{claim.giftQuantity} 份</span></div>
          <Kv rows={[
            { label: '禮物價格', value: claim.giftPrice != null ? `NT$ ${claim.giftPrice.toLocaleString()}` : '—' },
            { label: '寄出日期', value: formatPlainDate(claim.shippedOn) },
            { label: '物流業者', value: claim.trackingCarrier || '—' },
            { label: '追蹤碼', value: claim.trackingNumber || '—' },
            { label: '機構收到日期', value: claim.receivedOn ? formatPlainDate(claim.receivedOn) : '—' },
          ]} />
        </DCard>
      )}
      {claim ? (
        <div className="dpair">
          {org}
          <DCard icon="user" title="認領者">
            <p className="dlead">{claim.donorDisplayName}</p>
            <Kv rows={[
              { label: '捐贈人姓名', value: claim.donorName || '—', full: true },
              { label: '帳號信箱', value: claim.donorEmail, full: true },
              { label: '認領時間', value: formatDateTime(claim.claimedAt), full: true },
            ]} />
          </DCard>
        </div>
      ) : org}
      <DCard icon="gift" title="孩子的願望">
        <div className="dwish">
          {wish.avatar && <img className="avatar" src={avatarSrc(wish.avatar)} alt="" width={56} height={56} />}
          <div><strong>{wish.childAlias}</strong><span>{wish.ageRangeLabel}</span></div>
        </div>
        {wish.description && <p className="dquote">{wish.description}</p>}
      </DCard>
      <LazyBox key={`files-${wish.id}`} title="附件" button="載入附件"
        desc={`孩子的手寫信件${claim?.shippedAt ? '、寄送證明' : ''}可能含孩童影像${claim ? '與捐贈者個資' : ''}。`}
        load={() => api.get<AttachmentView[]>(`/api/admin/wishes/${wish.id}/files`)}>
        {(files: AttachmentView[]) => files.length === 0
          ? <p className="auth-note">沒有附件。</p>
          : <AttachmentGallery files={files} altPrefix="附件" />}
      </LazyBox>
      {claim && (
        <LazyBox key={`receipt-${claim.claimId}`} title="捐贈收據" secret button="載入捐贈收據資料"
          desc="含身分證字號或統一編號等個資，只有管理員看得到。"
          load={async () => {
            try {
              return await api.get<ClaimReceiptView>(`/api/admin/claims/${claim.claimId}/receipt`)
            } catch (e) {
              if (e instanceof ApiError && e.status === 404) return null
              throw e
            }
          }}>
          {(receipt: ClaimReceiptView | null) => receipt
            ? (
              <>
                <Kv rows={[
                  { label: '收據抬頭', value: receipt.name },
                  { label: '身分證字號或統一編號', value: receipt.idNumber },
                  { label: '收據寄送信箱', value: receipt.email },
                  { label: '收據寄送地址', value: receipt.address },
                ]} />
                {receipt.proofs.length > 0 && <AttachmentGallery files={receipt.proofs} altPrefix="發票證明" />}
              </>
            )
            : <p className="auth-note">這筆認領沒有另外索取捐贈收據；協會仍依捐贈人姓名（或「善心人士」）開立。</p>}
        </LazyBox>
      )}
    </>
  )
}
