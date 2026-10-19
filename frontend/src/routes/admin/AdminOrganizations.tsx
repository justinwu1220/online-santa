import { ChevronRight } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ConfirmDialog } from '../../components/console/ConfirmDialog'
import { Drawer } from '../../components/console/Drawer'
import { DCard, DStatus, DrawerTabs, Kv } from '../../components/console/DrawerParts'
import { LazyBox } from '../../components/console/LazyBox'
import { ReasonDialog } from '../../components/console/ReasonDialog'
import { useToast } from '../../components/ui/toastContext'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { ApiError, api } from '../../lib/api'
import { pageTitle } from '../../lib/brand'
import { formatDate, formatDateTime } from '../../lib/format'
import { burst, center, reducedMotion, rise } from '../../lib/motion'
import type { AttachmentView, OrganizationReviewView, OrganizationStatus } from '../../lib/types'
import { useAdminOrganizations, useInvalidateAdmin } from './adminQueries'

type Filter = 'all' | 'pending' | 'approved' | 'rejected' | 'suspended'

const STATUS_LABEL: Record<OrganizationStatus, string> = {
  PENDING: '待審核', APPROVED: '已核准', REJECTED: '已退件', SUSPENDED: '已停權',
}
/** 清單標籤與抽屜色調的 data-s／data-tone（設計稿用 returned 代表退件）。 */
const CHIP: Record<OrganizationStatus, string> = {
  PENDING: 'pending', APPROVED: 'approved', REJECTED: 'returned', SUSPENDED: 'suspended',
}
const RANK: Record<OrganizationStatus, number> = { PENDING: 0, APPROVED: 1, REJECTED: 2, SUSPENDED: 3 }
const FILTERS: { key: Filter; label: string; status?: OrganizationStatus }[] = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待審核', status: 'PENDING' },
  { key: 'approved', label: '已核准', status: 'APPROVED' },
  { key: 'rejected', label: '已退件', status: 'REJECTED' },
  { key: 'suspended', label: '已停權', status: 'SUSPENDED' },
]

/** 清單右側的日期說明：申請於／核准於／退回於／停權於。 */
function whenLabel(org: OrganizationReviewView) {
  if (org.status === 'PENDING') return `申請於 ${formatDate(org.createdAt)}`
  if (!org.reviewedAt) return ''
  const verb = { APPROVED: '核准於', REJECTED: '退回於', SUSPENDED: '停權於' }[org.status]
  return `${verb} ${formatDate(org.reviewedAt)}`
}

/**
 * 機構審核（設計稿 16.2）：篩選（預設待審核）、列表、抽屜（機構資料／照片與文件）、
 * 核准、退回（必填原因）、停權、解除停權。照片與文件是點擊載入，載入時後端各寫一筆稽核。
 */
export function AdminOrganizations() {
  const [params, setParams] = useSearchParams()
  const filter = (FILTERS.find((item) => item.key === params.get('filter'))?.key ?? 'pending') as Filter
  const orgs = useAdminOrganizations()
  const listRef = useRef<HTMLDivElement>(null)
  const [openId, setOpenId] = useState<string | null>(null)

  useEffect(() => {
    document.title = pageTitle('機構審核｜管理員後台')
    return () => { document.title = pageTitle() }
  }, [])

  const all = orgs.data?.content
  const counts = useMemo(() => {
    const result: Record<Filter, number> = { all: 0, pending: 0, approved: 0, rejected: 0, suspended: 0 }
    for (const org of all ?? []) {
      result.all += 1
      result[FILTERS.find((item) => item.status === org.status)!.key] += 1
    }
    return result
  }, [all])

  const status = FILTERS.find((item) => item.key === filter)?.status
  // 全部：待審核 → 已核准 → 已退件 → 已停權，同狀態內舊的在前
  const rows = useMemo(() => (all ?? [])
    .filter((org) => !status || org.status === status)
    .sort((a, b) => RANK[a.status] - RANK[b.status] || a.createdAt.localeCompare(b.createdAt)), [all, status])

  function chooseFilter(next: Filter) {
    setParams({ filter: next }, { replace: true })
    if (!reducedMotion()) {
      window.requestAnimationFrame(() => rise(
        [...(listRef.current?.querySelectorAll('.org-row') ?? [])],
        { delay: 0, stagger: 30, dist: 8, dur: 320 }))
    }
  }

  return (
    <section className="org-tab" aria-labelledby="adOrgTitle">
      <div className="org-head"><h1 id="adOrgTitle">機構審核</h1></div>

      <div className="org-filters" role="tablist" aria-label="依審核狀態篩選">
        {FILTERS.map((item) => (
          <button key={item.key} type="button" role="tab" className="org-filter"
            aria-selected={item.key === filter} onClick={() => chooseFilter(item.key)}>
            {item.label}<span>{counts[item.key]}</span>
          </button>
        ))}
      </div>

      {orgs.isLoading && <Spinner label="載入機構" />}
      {orgs.isError && <ErrorNotice error={orgs.error} onRetry={() => void orgs.refetch()} />}
      {orgs.data && (
        <div className="org-list" role="list" ref={listRef}>
          {rows.length === 0 ? (
            <p className="org-empty">{filter === 'pending' ? '目前沒有等待審核的機構。' : '這個分類目前沒有機構。'}</p>
          ) : rows.map((org) => (
            <button key={org.id} type="button" className="org-row" role="listitem"
              aria-label={`${STATUS_LABEL[org.status]}，${org.name}`} onClick={() => setOpenId(org.id)}>
              <span className="st-chip" data-s={CHIP[org.status]}>{STATUS_LABEL[org.status]}</span>
              <span className="org-row__title">{org.name}
                <span className="org-row__sub">{[org.contactPerson, org.mobilePhone].filter(Boolean).join('・')}</span>
              </span>
              <span className="org-row__who">{whenLabel(org)}</span>
              <span className="org-row__badges" />
              <ChevronRight className="icon org-row__go" aria-hidden />
            </button>
          ))}
        </div>
      )}

      <OrgDrawer org={all?.find((item) => item.id === openId) ?? null} onClose={() => setOpenId(null)} />
    </section>
  )
}

function OrgDrawer({ org, onClose }: { org: OrganizationReviewView | null; onClose: () => void }) {
  const toast = useToast()
  const invalidate = useInvalidateAdmin()
  const [tab, setTab] = useState('info')
  const [dialog, setDialog] = useState<'return' | 'suspend' | 'unsuspend' | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  // 換一間機構（或重新打開）回到第一個分頁；留住最後一間的資料，關閉動畫期間內容不會消失
  const [shown, setShown] = useState<OrganizationReviewView | null>(org)
  const [lastId, setLastId] = useState<string | null>(null)
  if (org && org.id !== lastId) { setLastId(org.id); setTab('info') }
  if (org && org !== shown) setShown(org)
  const current = org ?? shown
  const open = org !== null

  async function act(path: string, body: unknown, message: string, celebrate?: Element | null) {
    if (!current) return
    await api.post(`/api/admin/organizations/${current.id}/${path}`, body)
    if (celebrate && !reducedMotion()) burst(...center(celebrate), 16, 100)
    toast.show(message)
    await invalidate()
  }

  async function approve(button: HTMLElement) {
    if (!current || busy) return
    setBusy(true)
    try {
      await act('approve', {}, `已核准「${current.name}」，系統已寄信通知`, button)
      onClose()
    } catch (e) {
      toast.show(e instanceof ApiError ? e.detail : '核准失敗，請稍後再試', 'danger')
    } finally {
      setBusy(false)
    }
  }

  if (!current) return null
  const tone = CHIP[current.status]
  const status = {
    PENDING: <DStatus tone="review" icon="search" title="等你審核"
      desc={`申請於 ${formatDateTime(current.createdAt)}。請核對資料，並在「照片與文件」分頁看過證明文件，再核准或退回。`} />,
    APPROVED: <DStatus tone="open" icon="check" title="已核准"
      desc={`${formatDateTime(current.reviewedAt)} 核准，機構可以提交願望。`} />,
    REJECTED: <DStatus tone="returned" icon="alert" title="已退回，等機構修改"
      desc={`退回原因：${current.reviewNote ?? ''}`} />,
    SUSPENDED: <DStatus tone="frozen" icon="alert" title="已停權"
      desc={`${current.reviewedAt ? formatDateTime(current.reviewedAt) + ' 停權。' : ''}機構無法登入後台與刊登願望；已上架的願望不受影響。`} />,
  }[current.status]

  // 已核准／已停權的機構才有願望可看：連結放在底部按鈕列的最左側，其餘動作靠右
  const wishesLink = current.status === 'APPROVED' || current.status === 'SUSPENDED' ? (
    <Link className="btn" style={{ marginRight: 'auto' }} to={`/admin/wishes?org=${current.id}`}>查看這間機構的願望</Link>
  ) : null
  const foot = wishesLink || current.status === 'PENDING' ? (
    <div className="drawer__foot">
      {wishesLink}
      {current.status === 'PENDING' && (
        <>
          <button type="button" className="btn btn--danger" onClick={() => setDialog('return')}>退回</button>
          <button type="button" className="btn btn--tone-solid" disabled={busy}
            onClick={(event) => void approve(event.currentTarget)}>核准</button>
        </>
      )}
      {current.status === 'APPROVED' && (
        <button type="button" className="btn btn--freeze" onClick={() => setDialog('suspend')}>停權這個機構</button>
      )}
      {current.status === 'SUSPENDED' && (
        <button type="button" className="btn btn--freeze" onClick={() => setDialog('unsuspend')}>解除停權</button>
      )}
    </div>
  ) : undefined

  return (
    <>
      <Drawer open={open} onClose={onClose} tone={tone} labelledBy="adDrawerTitle"
        chip={<span className="st-chip" data-s={tone}>{STATUS_LABEL[current.status]}</span>}
        title={current.name}
        tabs={<DrawerTabs tabs={[{ key: 'info', label: '機構資料' }, { key: 'docs', label: '照片與文件' }]}
          active={tab} onChange={setTab} />}
        foot={foot}>
        <div className="drawer__body">
          {tab === 'info' && (
            <>
              {status}
              <DCard icon="user" title="承辦人與聯絡" className="dcard--key">
                <Kv rows={[
                  { label: '承辦人姓名', value: current.contactPerson },
                  { label: '行動電話', value: current.mobilePhone || '—' },
                  { label: '聯絡電話', value: current.contactPhone || '—' },
                  { label: '申請時間', value: formatDateTime(current.createdAt) },
                  { label: '聯絡信箱', value: current.contactEmail, full: true },
                  { label: '機構官方網站', full: true, value: current.website
                    ? <a href={current.website} target="_blank" rel="noopener noreferrer">{current.website}</a> : '—' },
                ]} />
              </DCard>
              <DCard icon="pin" title="收件">
                <Kv rows={[
                  { label: '收件地址', value: current.address || '—', full: true },
                  { label: '方便收件時間', value: current.preferredDeliveryTime || '—', full: true },
                ]} />
              </DCard>
              <DCard icon="building" title="機構簡介">
                <p className="dtext">{current.description || '—'}</p>
              </DCard>
            </>
          )}
          {tab === 'docs' && (
            <>
              <LazyBox key={`photo-${current.id}`} title="機構照片" desc="照片可能拍到孩子或機構環境。" button="載入照片"
                load={async () => (await api.get<{ url?: string }>(`/api/admin/organizations/${current.id}/photo`)).url ?? null}>
                {(url: string | null) => url
                  ? <div className="att-grid"><a href={url} target="_blank" rel="noopener noreferrer">
                    <img src={url} alt="機構照片" style={{ width: 240, height: 160, objectFit: 'cover', borderRadius: 12 }} /></a></div>
                  : <p className="auth-note">機構還沒有上傳照片。</p>}
              </LazyBox>
              <LazyBox key={`docs-${current.id}`} title="相關文件證明" secret button="載入文件"
                desc="含立案字號、負責人等資料。"
                load={() => api.get<AttachmentView[]>(`/api/admin/organizations/${current.id}/documents`)}>
                {(docs: AttachmentView[]) => docs.length === 0
                  ? <p className="auth-note">機構沒有上傳文件。</p>
                  : <div className="att-grid">{docs.map((doc) => (
                    <a key={doc.id} className="att att--photo" href={doc.url} target="_blank" rel="noopener noreferrer">
                      {doc.fileName ?? '文件'}</a>))}</div>}
              </LazyBox>
            </>
          )}
        </div>
      </Drawer>

      <ReasonDialog open={dialog === 'return'} onClose={() => setDialog(null)}
        title={`退回「${current.name}」的申請？`} body="機構會收到通知與退回原因，修改後可以重新送出申請。"
        label="退回原因" hint="會原封不動寄給對方，請具體說明需要補充或修改的地方" minLength={5}
        tooShort="請寫下具體的退回原因（至少 5 個字）" okText="退回"
        onSubmit={async (reason) => {
          await act('reject', { note: reason }, `已退回「${current.name}」，系統已寄信通知`)
          setDialog(null)
          onClose()
        }} />

      <ReasonDialog open={dialog === 'suspend'} onClose={() => setDialog(null)} tone="freeze"
        title={`停權「${current.name}」？`}
        body="停權後這個機構無法登入後台、無法刊登新願望；已上架與已認領的願望不受影響。之後可以解除停權。"
        label="停權原因" hint="只有管理員看得到，會寫進稽核紀錄" minLength={2} rows={3}
        tooShort="請寫下停權原因" okText="停權"
        onSubmit={async (reason) => {
          await act('suspend', { note: reason }, `已停權「${current.name}」`)
          setDialog(null)
          onClose()
        }} />

      <ConfirmDialog open={dialog === 'unsuspend'} onClose={() => { setDialog(null); setError('') }}
        title={`解除「${current.name}」的停權？`} body="解除後機構可以重新登入後台與刊登願望。"
        okText="解除停權" tone="primary" busy={busy} error={error}
        onOk={async () => {
          setBusy(true)
          try {
            await act('reactivate', {}, `已解除「${current.name}」的停權`)
            setDialog(null)
            onClose()
          } catch (e) {
            setError(e instanceof ApiError ? e.detail : '解除失敗，請稍後再試')
          } finally {
            setBusy(false)
          }
        }} />
    </>
  )
}
