import { ChevronRight, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { ConfirmDialog } from '../../components/console/ConfirmDialog'
import { Drawer } from '../../components/console/Drawer'
import { DCard, DStatus, Kv } from '../../components/console/DrawerParts'
import { LazyBox } from '../../components/console/LazyBox'
import { ProfileChangeBox } from './ProfileChanges'
import { ReasonDialog } from '../../components/console/ReasonDialog'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { useToast } from '../../components/ui/toastContext'
import { ApiError, api } from '../../lib/api'
import { pageTitle } from '../../lib/brand'
import { formatDateTime } from '../../lib/format'
import type { AdminUserDetail, AdminUserView } from '../../lib/types'
import { useAdminUsers, useInvalidateAdmin } from './adminQueries'

const RESTRICTION_LABELS: Record<string, string> = {
  NONE: '正常', WISH_FROZEN: '願望凍結中', ORG_SUSPENDED: '機構停權中', DONOR_SUSPENDED: '認領者停權中（暫停）',
}

/**
 * 管理員「認領者」：搜尋認領者、處置違規行為。
 * 停權＝受限登入（可登入看通知，所有寫入動作被擋），他進行中認領的願望凍結、機構後台標示；
 * 還沒寄出的認領可以釋出讓願望回到願望牆；評估無風險後可解除停權。
 * 真實姓名等個資只在抽屜「點擊載入」，載入寫稽核。
 */
export function AdminUsers() {
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [openId, setOpenId] = useState<string | null>(null)
  const users = useAdminUsers(q)

  useEffect(() => {
    document.title = pageTitle('認領者｜管理員後台')
    return () => { document.title = pageTitle() }
  }, [])
  // 搜尋輸入停 300ms 才打 API
  useEffect(() => {
    const timer = window.setTimeout(() => setQ(search.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [search])

  return (
    <section className="org-tab" aria-labelledby="adUsersTitle">
      <div className="org-head">
        <h1 id="adUsersTitle">認領者</h1>
        <div className="ad-tools">
          <label className="ad-search">
            <Search className="icon" aria-hidden />
            <span className="sr-only">搜尋認領者</span>
            <input className="input" type="search" placeholder="搜尋信箱、顯示名稱或姓名"
              value={search} onChange={(event) => setSearch(event.target.value)} />
          </label>
        </div>
      </div>

      {users.isLoading && <Spinner label="載入認領者" />}
      {users.isError && <ErrorNotice error={users.error} onRetry={() => void users.refetch()} />}
      {users.data && (
        <div className="org-list" role="list">
          {users.data.content.length === 0 ? <p className="org-empty">沒有符合的認領者。</p>
            : users.data.content.map((user) => (
              <button key={user.id} type="button" className="org-row" role="listitem"
                data-s={user.suspended ? 'frozen' : 'open'} onClick={() => setOpenId(user.id)}>
                <span className="st-chip" data-s={user.suspended ? 'frozen' : 'open'}>{user.suspended ? '已停權' : '正常'}</span>
                <span className="org-row__title">{user.displayName ?? user.email}
                  <span className="org-row__sub">{user.email}</span>
                </span>
                <span className="org-row__who" />
                <span className="org-row__badges" />
                <ChevronRight className="icon org-row__go" aria-hidden />
              </button>
            ))}
        </div>
      )}

      <UserDrawer userId={openId} summary={users.data?.content.find((user) => user.id === openId)}
        onClose={() => setOpenId(null)} />
    </section>
  )
}

function UserDrawer({ userId, summary, onClose }: {
  userId: string | null
  summary?: AdminUserView
  onClose: () => void
}) {
  const toast = useToast()
  const invalidate = useInvalidateAdmin()
  const [dialog, setDialog] = useState<'suspend' | 'reinstate' | null>(null)
  const [releaseId, setReleaseId] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [lastId, setLastId] = useState<string | null>(null)
  const [shown, setShown] = useState<AdminUserView | null>(null)
  if (userId && userId !== lastId) { setLastId(userId); setReload(0) }
  if (summary && summary !== shown) setShown(summary)
  const user = summary ?? (userId === shown?.id ? shown : null)
  if (!user) return null

  async function act(path: string, body: unknown, message: string) {
    await api.post(path, body)
    toast.show(message)
    await invalidate()
    setReload((n) => n + 1)
  }

  const foot = (
    <div className="drawer__foot">
      {user.suspended
        ? <button type="button" className="btn btn--freeze" onClick={() => setDialog('reinstate')}>解除停權</button>
        : <button type="button" className="btn btn--freeze" onClick={() => setDialog('suspend')}>停權這位認領者</button>}
    </div>
  )

  return (
    <>
      <Drawer open={userId !== null} onClose={onClose} tone={user.suspended ? 'frozen' : 'open'}
        labelledBy="adUserTitle" title={user.displayName ?? user.email}
        chip={<span className="st-chip" data-s={user.suspended ? 'frozen' : 'open'}>{user.suspended ? '已停權' : '正常'}</span>}
        foot={foot}>
        <div className="drawer__body">
          {user.suspended ? (
            <DStatus tone="frozen" icon="alert" title="已停權（受限登入）"
              desc={<>{user.suspendedAt ? formatDateTime(user.suspendedAt) + '：' : ''}{user.suspendedReason}<br />
                仍可登入看停權通知，但所有寫入動作被擋；他進行中認領的願望已凍結，機構後台會標示「認領者出現違規行為，正在積極處理中。」</>} />
          ) : (
            <DStatus tone="open" icon="check" title="正常" desc="停權後他進行中的認領會凍結，由你決定釋出或維持。" />
          )}
          <LazyBox key={`user-${user.id}-${reload}`} title="個資與進行中的認領" secret button="載入"
            desc="含真實姓名、電話與認領清單。每次載入都會寫入稽核紀錄。"
            load={() => api.get<AdminUserDetail>(`/api/admin/users/${user.id}`)}>
            {(detail: AdminUserDetail) => (
              <>
                <DCard icon="user" title="認領者">
                  <Kv rows={[
                    { label: '帳號信箱', value: detail.user.email, full: true },
                    { label: '顯示名稱', value: detail.user.displayName || '—' },
                    { label: '真實姓名', value: detail.user.name || '—' },
                    { label: '電話', value: detail.phone || '—' },
                    { label: '註冊時間', value: formatDateTime(detail.user.createdAt), full: true },
                  ]} />
                </DCard>
                <DCard icon="gift" title={`進行中的認領（${detail.claims.length}）`}>
                  {detail.claims.length === 0
                    ? <p className="auth-note">沒有進行中的認領。</p>
                    : detail.claims.map((claim) => (
                      <div key={claim.claimId} className="dwish" style={{ marginBottom: 12 }}>
                        <div>
                          <strong>{claim.wishTitle}</strong>
                          <span>{claim.organizationName}・{RESTRICTION_LABELS[claim.restriction] ?? claim.restriction}</span>
                        </div>
                        {claim.canRelease && (
                          <button type="button" className="btn btn--sm btn--danger" onClick={() => setReleaseId(claim.claimId)}>
                            釋出這筆認領
                          </button>
                        )}
                        {!claim.canRelease && <span className="auth-note">已寄出，不能釋出（機構確認收到實體禮物）</span>}
                      </div>
                    ))}
                </DCard>
              </>
            )}
          </LazyBox>
          <ProfileChangeBox targetType="USER" targetId={user.id} />
        </div>
      </Drawer>

      <ReasonDialog open={dialog === 'suspend'} onClose={() => setDialog(null)} tone="freeze" rows={3}
        title={`停權「${user.displayName ?? user.email}」？`}
        body="受限登入：他仍可登入看停權通知，但無法認領、傳訊、回報寄送、取消或修改資料。他進行中認領的願望會凍結（還沒寄出的暫停寄送期限），機構後台標示「認領者出現違規行為」。會保全他當下的資訊，並寄信通知他。"
        label="停權原因" hint="會寄給認領者本人，也會寫進稽核紀錄" minLength={4} tooShort="請寫下停權原因（至少 4 個字）" okText="停權"
        onSubmit={async (reason) => {
          await act(`/api/admin/users/${user.id}/suspend`, { note: reason }, `已停權「${user.displayName ?? user.email}」`)
          setDialog(null)
        }} />
      <ConfirmDialog open={dialog === 'reinstate'} onClose={() => { setDialog(null); setError('') }}
        title={`解除「${user.displayName ?? user.email}」的停權？`}
        body="功能恢復正常；因停權而凍結的願望解除（其他原因的凍結保留），保留的認領繼續，寄送期限順延停權的時間。"
        okText="解除停權" tone="primary" busy={busy} error={error}
        onOk={async () => {
          setBusy(true)
          try {
            await act(`/api/admin/users/${user.id}/reactivate`, {}, '已解除停權')
            setDialog(null)
          } catch (e) {
            setError(e instanceof ApiError ? e.detail : '解除失敗，請稍後再試')
          } finally {
            setBusy(false)
          }
        }} />
      <ReasonDialog open={releaseId !== null} onClose={() => setReleaseId(null)}
        title="釋出這筆認領？" body="認領結束、願望回到願望牆讓其他人認領，認領者會收到通知、不需要再寄，機構會在對話看到系統訊息。"
        label="釋出原因" hint="會寄給認領者，也會寫進稽核紀錄" minLength={4} tooShort="請寫下釋出原因（至少 4 個字）" okText="釋出"
        onSubmit={async (reason) => {
          await act(`/api/admin/claims/${releaseId}/release`, { note: reason }, '已釋出，願望回到願望牆')
          setReleaseId(null)
        }} />
    </>
  )
}
