import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, withQuery } from '../lib/api'
import type { PageResponse, WishFilterOptions, WishPublicView } from '../lib/types'
import { EmptyState, ErrorBanner, Spinner } from '../components/Feedback'
import { Select } from '../components/Form'
import { Pagination } from '../components/Pagination'

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
      <header className="mb-6">
        <h1 className="text-3xl font-bold text-santa-700">今年的願望</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          每一則願望背後都是一個孩子。挑一個你想幫忙實現的，剩下的我們陪你走完。
        </p>
      </header>

      <div className="mb-6 grid gap-3 rounded-xl bg-white p-4 ring-1 ring-santa-100 sm:grid-cols-3">
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
      <span className="text-xs font-medium text-slate-500">{label}</span>
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
      className="group flex flex-col overflow-hidden rounded-xl bg-white ring-1 ring-santa-100
        transition-shadow hover:shadow-lg focus:outline-none focus:ring-2 focus:ring-santa-500"
    >
      <div className="aspect-[4/3] overflow-hidden bg-santa-50">
        {wish.imageUrl ? (
          <img
            src={wish.imageUrl}
            alt=""
            loading="lazy"
            className="h-full w-full object-cover transition-transform group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full items-center justify-center text-5xl">🎁</div>
        )}
      </div>

      <div className="flex flex-1 flex-col p-4">
        <h2 className="font-semibold text-slate-800">{wish.title}</h2>
        {/* 只顯示暱稱與年齡區間——系統裡本來就沒有孩子的真實姓名 */}
        <p className="mt-1 text-sm text-slate-500">
          {wish.childAlias}・{wish.ageRangeLabel}
        </p>
        {wish.interests && (
          <p className="mt-2 line-clamp-2 text-sm text-slate-600">{wish.interests}</p>
        )}

        <div className="mt-auto flex flex-wrap items-center gap-2 pt-4 text-xs">
          <span className="rounded-full bg-santa-50 px-2 py-0.5 text-santa-700">
            {wish.categoryLabel}
          </span>
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
            {wish.priceRangeLabel}
          </span>
        </div>
        <p className="mt-2 truncate text-xs text-slate-400">{wish.organizationName}</p>
      </div>
    </Link>
  )
}
