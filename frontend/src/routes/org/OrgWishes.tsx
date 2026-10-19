import { ChevronRight, MessageCircle, Plus } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { pageTitle } from '../../lib/brand'
import { reducedMotion, rise } from '../../lib/motion'
import type { OrganizationStageCounts, WishOrgView } from '../../lib/types'
import { compareByStage, FILTERS, STAGE_INFO, type FilterKey } from '../../lib/wishFlow'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { useOrgContext } from './orgContext'
import { OrgWishDrawer } from './OrgWishDrawer'
import { OrgWishFormDrawer } from './OrgWishFormDrawer'
import { useOrgWishes, useStageCounts } from './orgQueries'

/** 篩選列各項的數量。後端給的是各狀態與「已認領」的合計，這裡對應到設計稿的六個篩選。 */
function countFor(key: FilterKey, counts?: OrganizationStageCounts): number {
  if (!counts) return 0
  switch (key) {
    case 'all': return counts.all
    case 'draft': return counts.draft
    case 'review': return counts.pendingReview
    case 'open': return counts.open
    case 'claimed': return counts.claimed
    case 'done': return counts.done
  }
}

/**
 * 願望管理（設計稿 15.3）：右上「新增願望」、篩選列（各帶數量）、一列一個願望，
 * 點一列在右側開抽屜。列表只顯示狀態標籤、標題、暱稱・年齡，和「N 則新訊息」一種提示——
 * 狀態變化（寄出、逾期、退回…）一律以系統訊息通知，不再用額外的字樣。
 */
export function OrgWishes() {
  const { organization } = useOrgContext()
  const [params, setParams] = useSearchParams()
  const filter = (FILTERS.find((item) => item.key === params.get('filter'))?.key ?? 'all') as FilterKey
  const status = FILTERS.find((item) => item.key === filter)?.status
  const counts = useStageCounts()
  const wishes = useOrgWishes(status)

  const [openId, setOpenId] = useState<string | null>(null)
  const [form, setForm] = useState<{ open: boolean; wish: WishOrgView | null }>({ open: false, wish: null })
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    document.title = pageTitle('願望管理｜機構後台')
    return () => { document.title = pageTitle() }
  }, [])

  const rows = useMemo(
    () => [...(wishes.data?.content ?? [])].sort((a, b) => compareByStage(a.stage, b.stage)),
    [wishes.data])

  function chooseFilter(next: FilterKey) {
    setParams(next === 'all' ? {} : { filter: next }, { replace: true })
    // 換篩選：列依序由下 8px 浮上（設計稿 rise(0, 30, 8, 320)）
    if (!reducedMotion()) {
      window.requestAnimationFrame(() => rise(
        [...(listRef.current?.querySelectorAll('.org-row') ?? [])],
        { delay: 0, stagger: 30, dist: 8, dur: 320 }))
    }
  }

  return (
    <section className="org-tab" aria-labelledby="orgWishTitle">
      <div className="org-head">
        <h1 id="orgWishTitle">願望管理</h1>
        <button type="button" className="btn btn--primary org-head__new"
          disabled={!organization.canDraftWishes}
          title={organization.canDraftWishes ? undefined : '機構已停權，無法建立新願望'}
          onClick={() => setForm({ open: true, wish: null })}>
          <Plus className="icon" aria-hidden />
          新增願望
        </button>
      </div>

      <div className="org-filters" role="tablist" aria-label="依狀態篩選">
        {FILTERS.map((item) => (
          <button key={item.key} type="button" role="tab" className="org-filter"
            aria-selected={item.key === filter} onClick={() => chooseFilter(item.key)}>
            {item.label}<span>{countFor(item.key, counts.data)}</span>
          </button>
        ))}
      </div>

      {wishes.isLoading && <Spinner label="載入願望" />}
      {wishes.isError && <ErrorNotice error={wishes.error} onRetry={() => void wishes.refetch()} />}

      {wishes.data && (
        <div className="org-list" role="list" ref={listRef}>
          {rows.length === 0 ? (
            counts.data && counts.data.all > 0
              ? <p className="org-empty">這個分類目前沒有願望。</p>
              : <div className="org-empty"><p>還沒有任何願望。按右上角「新增願望」，把孩子的願望放上來吧。</p></div>
          ) : rows.map((wish) => <WishRow key={wish.id} wish={wish} onOpen={() => setOpenId(wish.id)} />)}
        </div>
      )}

      <OrgWishDrawer
        wishId={openId}
        onClose={() => setOpenId(null)}
        onEdit={(wish) => { setOpenId(null); setForm({ open: true, wish }) }}
      />
      <OrgWishFormDrawer
        open={form.open}
        wish={form.wish}
        onClose={() => setForm((current) => ({ ...current, open: false }))}
        onSaved={() => { setForm((current) => ({ ...current, open: false })); chooseFilter('all') }}
      />
    </section>
  )
}

function WishRow({ wish, onOpen }: { wish: WishOrgView; onOpen: () => void }) {
  const info = STAGE_INFO[wish.stage]
  return (
    <button type="button" className="org-row" data-s={info.chip} data-id={wish.id} role="listitem"
      aria-label={[info.label, wish.title, wish.childAlias, wish.ageRangeLabel].filter(Boolean).join('，')}
      onClick={onOpen}>
      <span className="st-chip" data-s={info.chip}>{info.label}</span>
      <span className="org-row__title">{wish.title}</span>
      <span className="org-row__who">{[wish.childAlias, wish.ageRangeLabel].filter(Boolean).join('・')}</span>
      <span className="org-row__badges">
        {wish.unreadCount > 0 && (
          <span className="badge badge--msg">
            <MessageCircle className="icon" aria-hidden />{wish.unreadCount} 則新訊息
          </span>
        )}
      </span>
      <ChevronRight className="icon org-row__go" aria-hidden />
    </button>
  )
}
