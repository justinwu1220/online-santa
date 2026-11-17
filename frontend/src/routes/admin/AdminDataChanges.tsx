import { useQuery } from '@tanstack/react-query'
import { ChevronRight } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Drawer } from '../../components/console/Drawer'
import { Pager } from '../../components/ui/Pager'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { api, withQuery } from '../../lib/api'
import { pageTitle } from '../../lib/brand'
import { formatDateTime } from '../../lib/format'
import type {
  PageResponse, ProfileChangeTargetType, ProfileChangeTargetView, ProfileChangeView,
} from '../../lib/types'
import { ProfileChangeRows } from './ProfileChanges'

const TABS: { key: ProfileChangeTargetType; label: string }[] = [
  { key: 'USER', label: '認領者' },
  { key: 'ORGANIZATION', label: '機構' },
]

/**
 * 個資異動紀錄：誰在什麼時候把哪個欄位從什麼改成什麼。
 *
 * <p>目的是爭議舉證——上線後若有人主張「我沒有改過地址」「這支電話不是我填的」，這裡
 * 是唯一答得出來的地方。
 *
 * <p>分成兩層：清單只列「改過資料的對象」（沒有紀錄的不出現，否則真正要看的幾筆會被
 * 淹掉），而且不含任何欄位值所以不寫稽核；點開抽屜才看得到實際改了什麼，<strong>那一步
 * 會寫一筆稽核</strong>。
 */
export function AdminDataChanges() {
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find((item) => item.key === params.get('tab'))?.key ?? 'USER') as ProfileChangeTargetType
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState<ProfileChangeTargetView | null>(null)

  useEffect(() => {
    document.title = pageTitle('個資異動紀錄｜管理員後台')
    return () => { document.title = pageTitle() }
  }, [])

  function chooseTab(key: ProfileChangeTargetType) {
    const next = new URLSearchParams(params)
    next.set('tab', key)
    setParams(next, { replace: true })
    setPage(0)
  }

  const targets = useQuery({
    queryKey: ['profile-change-targets', tab, page],
    queryFn: () => api.get<PageResponse<ProfileChangeTargetView>>(
      withQuery('/api/admin/profile-changes/targets', { targetType: tab, page, size: 30 })),
  })

  return (
    <section className="org-tab" aria-labelledby="adChangesTitle">
      <div className="org-head"><h1 id="adChangesTitle">個資異動紀錄</h1></div>

      <p className="auth-note">
        只列出改過自己資料的對象，沒有異動的不會出現。點開看明細時
        <strong>會寫入一筆稽核紀錄</strong>，在「系統與稽核」頁查得到誰看過。
      </p>

      <div className="org-filters" role="tablist" aria-label="依對象別切換">
        {TABS.map((item) => (
          <button key={item.key} type="button" role="tab" className="org-filter"
            aria-selected={item.key === tab} onClick={() => chooseTab(item.key)}>
            {item.label}
            {targets.data && item.key === tab && <span>{targets.data.totalElements}</span>}
          </button>
        ))}
      </div>

      {targets.isLoading && <Spinner label="載入清單" />}
      {targets.isError && <ErrorNotice error={targets.error} onRetry={() => void targets.refetch()} />}
      {targets.data && (
        <>
          <div className="org-list" role="list">
            {targets.data.content.length === 0 ? (
              <p className="org-empty">
                {tab === 'USER' ? '還沒有認領者修改過自己的資料。' : '還沒有機構修改過資料。'}
              </p>
            ) : targets.data.content.map((target) => (
              /* .org-row 是五欄的 grid（chip／標題／說明／標籤／箭頭），少給一個子元素
                 後面全部會錯位，所以標籤欄即使沒有內容也要留著 */
              <button key={target.targetId} type="button" className="org-row" role="listitem"
                aria-label={`${target.label ?? target.targetId}，${target.changeCount} 次異動`}
                onClick={() => setOpen(target)}>
                <span className="st-chip" data-s="pending">{target.changeCount} 次</span>
                <span className="org-row__title">
                  {target.label ?? target.targetId}
                  <span className="org-row__sub">
                    {target.targetType === 'USER' ? '認領者' : '機構'}
                    {target.label ? '' : '（帳號已刪除）'}
                  </span>
                </span>
                <span className="org-row__who">最近異動 {formatDateTime(target.lastChangedAt)}</span>
                <span className="org-row__badges" />
                <ChevronRight className="icon org-row__go" aria-hidden />
              </button>
            ))}
          </div>
          <Pager page={targets.data} onChange={setPage} />
        </>
      )}

      <TargetDrawer target={open} onClose={() => setOpen(null)} />
    </section>
  )
}

/**
 * 單一對象的異動明細。開啟時才打 API，而那支每呼叫一次就寫一筆稽核——所以不要在
 * 清單那層先載入。
 */
function TargetDrawer({ target, onClose }: {
  target: ProfileChangeTargetView | null
  onClose: () => void
}) {
  // 關閉動畫期間還要顯示最後那個對象，所以記住最後一次
  const [last, setLast] = useState<ProfileChangeTargetView | null>(null)
  if (target && target !== last) setLast(target)
  const shown = target ?? last

  const changes = useQuery({
    queryKey: ['profile-changes', shown?.targetType ?? '', shown?.targetId ?? ''],
    enabled: target !== null,
    queryFn: () => api.get<PageResponse<ProfileChangeView>>(
      withQuery('/api/admin/profile-changes', {
        targetType: shown!.targetType, targetId: shown!.targetId, page: 0, size: 100,
      })),
  })

  return (
    <Drawer open={target !== null} onClose={onClose} tone="review" labelledBy="adChangeDrawerTitle"
      title={shown?.label ?? shown?.targetId ?? ''}
      chip={shown && (
        <span className="st-chip" data-s="pending">
          {shown.targetType === 'USER' ? '認領者' : '機構'}
        </span>
      )}>
      <div className="drawer__body">
        <p className="auth-note">
          這個對象自己修改資料的歷程，新的在前。紀錄只能新增，不能修改或刪除。
        </p>
        {changes.isLoading && <Spinner label="載入異動紀錄" />}
        {changes.isError && <ErrorNotice error={changes.error} onRetry={() => void changes.refetch()} />}
        {changes.data && <ProfileChangeRows rows={changes.data.content} />}
        {changes.data?.hasNext && (
          <p className="auth-note">紀錄超過 100 筆，這裡只顯示最近的 100 筆。</p>
        )}
      </div>
    </Drawer>
  )
}
