import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, withQuery } from '../lib/api'
import type { PageResponse, WishFilterOptions, WishPublicView } from '../lib/types'
import { EmptyState, ErrorBanner, Spinner } from '../components/Feedback'
import { Select } from '../components/Form'
import { Pagination } from '../components/Pagination'
import { wishIcon } from '../lib/wishIcon'

interface Filters {
  category: string
  ageRange: string
  priceRange: string
}

const EMPTY_FILTERS: Filters = { category: '', ageRange: '', priceRange: '' }

export function WishWall() {
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [page, setPage] = useState(0)

  // 篩選選項由後端提供，新增分類時前端會自動出現，不必兩邊同步
  const options = useQuery({
    queryKey: ['wish-options'],
    queryFn: () => api.get<WishFilterOptions>('/api/wishes/options'),
    staleTime: Infinity,
  })

  const wishes = useQuery({
    queryKey: ['wishes', filters, page],
    queryFn: () => api.get<PageResponse<WishPublicView>>(
      withQuery('/api/wishes', { ...filters, page, size: 12 })),
  })

  function updateFilter(key: keyof Filters, value: string) {
    setFilters((current) => ({ ...current, [key]: value }))
    setPage(0)
  }

  return (
    <section>
      <header className="mb-8 text-center">
        <h1 className="text-4xl font-bold tracking-wide md:text-5xl">
          <span className="bg-gradient-to-r from-red-300 via-white to-emerald-300
            bg-clip-text text-transparent drop-shadow">
            今年的願望
          </span>
        </h1>
        <p className="mx-auto mt-3 max-w-2xl text-slate-300">
          每一則願望背後都是一個孩子。挑一個你想幫忙實現的，剩下的我們陪你走完。
        </p>
      </header>

      <div className="glass-card mb-8 grid gap-4 p-5 sm:grid-cols-3">
        <FilterSelect label="分類" value={filters.category}
          options={options.data?.categories}
          onChange={(value) => updateFilter('category', value)} />
        <FilterSelect label="年齡" value={filters.ageRange}
          options={options.data?.ageRanges}
          onChange={(value) => updateFilter('ageRange', value)} />
        <FilterSelect label="預算" value={filters.priceRange}
          options={options.data?.priceRanges}
          onChange={(value) => updateFilter('priceRange', value)} />
      </div>

      {wishes.isLoading && <Spinner label="載入願望" />}
      {wishes.isError && (
        <ErrorBanner error={wishes.error} onRetry={() => void wishes.refetch()} />
      )}

      {wishes.data && wishes.data.content.length === 0 && (
        <EmptyState
          icon="🌟"
          title="這個條件下還沒有願望"
          hint={filters === EMPTY_FILTERS
            ? '機構還在上架中，過幾天再來看看。'
            : '試著放寬篩選條件。'}
        />
      )}

      {wishes.data && wishes.data.content.length > 0 && (
        <>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {wishes.data.content.map((wish) => <WishCard key={wish.id} wish={wish} />)}
          </div>
          <Pagination page={wishes.data} onChange={setPage} />
        </>
      )}
    </section>
  )
}

function FilterSelect({ label, value, options, onChange }: {
  label: string
  value: string
  options?: { value: string; label: string }[]
  onChange: (value: string) => void
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-slate-400">{label}</span>
      <Select className="mt-1" value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">全部</option>
        {options?.map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </Select>
    </label>
  )
}

function WishCard({ wish }: { wish: WishPublicView }) {
  return (
    <Link
      to={`/wishes/${wish.id}`}
      className="glass-card-interactive group flex flex-col overflow-hidden"
    >
      <div className="aspect-[4/3] overflow-hidden bg-white/5">
        {wish.imageUrl ? (
          <img
            src={wish.imageUrl}
            alt=""
            loading="lazy"
            className="h-full w-full object-cover transition-transform group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full items-center justify-center text-6xl opacity-90">
            {wishIcon(wish.category)}
          </div>
        )}
      </div>

      <div className="flex flex-1 flex-col p-5">
        <h2 className="text-lg font-bold text-white transition-colors
          group-hover:text-emerald-200">
          {wish.title}
        </h2>
        {/* 只顯示暱稱與年齡區間——系統裡本來就沒有孩子的真實姓名 */}
        <p className="mt-1 text-sm text-slate-400">
          {wish.childAlias}・{wish.ageRangeLabel}
        </p>
        {wish.interests && (
          <p className="mt-2 line-clamp-2 text-sm leading-relaxed text-slate-300">
            {wish.interests}
          </p>
        )}

        <div className="mt-auto flex flex-wrap items-center gap-2 pt-4">
          <span className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1
            text-xs font-medium text-emerald-200">
            {wish.categoryLabel}
          </span>
          <span className="night-chip">{wish.priceRangeLabel}</span>
        </div>
        <p className="mt-3 truncate text-xs text-slate-500">{wish.organizationName}</p>
      </div>
    </Link>
  )
}
