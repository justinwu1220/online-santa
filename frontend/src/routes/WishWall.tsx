import { useQuery } from '@tanstack/react-query'
import { Gift } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, withQuery } from '../lib/api'
import { avatarSrc } from '../lib/avatar'
import { useListEntry } from '../lib/useEntryAnimation'
import type { PageResponse, WishFilterOptions, WishListView } from '../lib/types'
import { publicStatusOf, WALL_STATUS_OPTIONS } from '../lib/wishStatus'
import { EmptyBox, ErrorNotice, Spinner } from '../components/ui/Spinner'
import { Pager } from '../components/ui/Pager'

interface Filters {
  organizationId: string
  ageRange: string
  status: string
}

const EMPTY_FILTERS: Filters = { organizationId: '', ageRange: '', status: '' }

export function WishWall() {
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [page, setPage] = useState(0)

  // 篩選選項由後端提供，新增年齡組或機構時前端會自動出現，不必兩邊同步
  const options = useQuery({
    queryKey: ['wish-options'],
    queryFn: () => api.get<WishFilterOptions>('/api/wishes/options'),
    staleTime: Infinity,
  })

  const wishes = useQuery({
    queryKey: ['wishes', filters, page],
    queryFn: () => api.get<PageResponse<WishListView>>(
      withQuery('/api/wishes', { ...filters, page, size: 20 })),
  })

  // 從詳細頁回來：還原捲動位置並淡入（SPEC 6.2）
  useListEntry('/', Boolean(wishes.data))

  function updateFilter(key: keyof Filters, value: string) {
    setFilters((current) => ({ ...current, [key]: value }))
    setPage(0)
  }

  const unfiltered = !filters.organizationId && !filters.ageRange && !filters.status

  return (
    <>
      <Hero />

      <section className="filters" aria-label="篩選願望">
        <FilterSelect label="機構名稱" value={filters.organizationId}
          options={options.data?.organizations}
          onChange={(value) => updateFilter('organizationId', value)} />
        <FilterSelect label="孩子年齡" value={filters.ageRange}
          options={options.data?.ageRanges}
          onChange={(value) => updateFilter('ageRange', value)} />
        <FilterSelect label="禮物狀態" value={filters.status}
          options={WALL_STATUS_OPTIONS}
          onChange={(value) => updateFilter('status', value)} />
      </section>

      {wishes.isLoading && <Spinner label="載入願望" />}
      {wishes.isError && <ErrorNotice error={wishes.error} onRetry={() => void wishes.refetch()} />}

      {wishes.data && wishes.data.content.length === 0 && (
        <EmptyBox icon="🌟" title="這個條件下還沒有願望"
          hint={unfiltered ? '機構還在上架中，過幾天再來看看。' : '試著放寬篩選條件。'} />
      )}

      {wishes.data && wishes.data.content.length > 0 && (
        <>
          <div className="wish-grid">
            {wishes.data.content.map((wish) => <WishCard key={wish.id} wish={wish} />)}
          </div>
          <Pager page={wishes.data} onChange={setPage} />
        </>
      )}
    </>
  )
}

/**
 * 聯名式大標（SPEC 4）。康乃心 logo 圖檔還在待確認（暫用檔不放進 repo），先只放協會名稱；
 * 勸募許可文號也待確認，確認前不顯示占位文字。
 */
function Hero() {
  return (
    <section className="hero" aria-labelledby="heroTitle">
      <div className="hero__row">
        <div className="hero__title-wrap">
          <svg className="hero__spark twinkle icon" viewBox="0 0 48 48" strokeWidth="2.6" aria-hidden>
            <path d="M20 10c1.6 7.6 4.4 10.4 12 12-7.6 1.6-10.4 4.4-12 12-1.6-7.6-4.4-10.4-12-12 7.6-1.6 10.4-4.4 12-12z" />
            <path d="M36 5v8M32 9h8" />
            <circle cx="9" cy="38" r="3" />
          </svg>
          <h1 className="hero__title grad-text" id="heroTitle">線上聖誕老人</h1>
        </div>
        <span className="hero__x twinkle-slow" aria-label="與">×</span>
        <div className="hero__org">
          <img className="hero__logo" src="/assets/logo/kangnaixin-logo.png" alt="康乃心關懷協會標誌" />
          <p className="hero__org-name">
            <small>社團法人彰化縣</small>
            <strong>康乃心關懷協會</strong>
          </p>
        </div>
      </div>
      <p className="hero__sub">溫暖傳遞，讓每個願望都閃閃發光</p>
    </section>
  )
}

function FilterSelect({ label, value, options, onChange }: {
  label: string
  value: string
  options?: readonly { value: string; label: string }[]
  onChange: (value: string) => void
}) {
  return (
    <label>
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">全部</option>
        {options?.map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>
    </label>
  )
}

/**
 * 願望卡：整張卡片是一個連結。不顯示禮物分類與孩子的信件文字（SPEC 4）；
 * 機構名稱完整顯示，不截斷。狀態只由 `data-status` 推導樣式。
 */
function WishCard({ wish }: { wish: WishListView }) {
  const status = publicStatusOf(wish.status)

  return (
    <Link
      to={`/wishes/${wish.id}`}
      className="wish-card"
      data-child={wish.childAlias}
      data-status={status.key}
      aria-label={`${wish.childAlias}，${wish.ageRangeLabel}，${wish.organizationName}，想要${wish.title}，${status.label}`}
    >
      <span className="status-tab">{status.label}</span>

      <div className="who">
        <img className="avatar" src={avatarSrc(wish.avatar)} alt="" width={64} height={64} />
        <div className="who__text">
          <div className="who__name-row">
            <span className="who__name">{wish.childAlias}</span>
            <span className="who__age">{wish.ageRangeLabel}</span>
          </div>
          <div className="who__org">{wish.organizationName}</div>
        </div>
      </div>

      <div className="gift-wrap">
        <span className="gift">
          <Gift className="icon" aria-hidden />
          <span className="gift__text">{wish.title}</span>
        </span>
      </div>
    </Link>
  )
}
