import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, withQuery } from '../../lib/api'
import { formatDate } from '../../lib/format'
import type {
  PageResponse, WishFilterOptions, WishOrgView, WishRequestBody, WishStatus,
} from '../../lib/types'
import { EmptyState, ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { Button, Field, Select, TextArea, TextInput } from '../../components/Form'
import { ImageUploader } from '../../components/ImageUploader'
import { Pagination } from '../../components/Pagination'
import { WishStatusBadge } from '../../components/StatusBadge'
import { WISH_IMAGE_ENABLED, wishIcon } from '../../lib/wishIcon'
import { useOrgContext } from './orgContext'

const STATUS_FILTERS: { value: WishStatus | ''; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'AVAILABLE', label: '上架中' },
  { value: 'CLAIMED', label: '已被認領' },
  { value: 'FULFILLED', label: '已完成' },
  { value: 'ARCHIVED', label: '已下架' },
]

export function OrgWishes() {
  const { organization } = useOrgContext()
  const queryClient = useQueryClient()
  // 從網址讀篩選條件，讓總覽頁的「草稿 3」之類的連結點得進來
  const [searchParams, setSearchParams] = useSearchParams()
  const status = (searchParams.get('status') ?? '') as WishStatus | ''
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<WishOrgView | 'new' | null>(null)

  const wishes = useQuery({
    queryKey: ['org-wishes', status, page],
    queryFn: () => api.get<PageResponse<WishOrgView>>(
      withQuery('/api/organizations/me/wishes', { status, page, size: 10 })),
  })

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['org-wishes'] })
    void queryClient.invalidateQueries({ queryKey: ['wishes'] })
  }

  if (editing) {
    return (
      <WishForm
        wish={editing === 'new' ? null : editing}
        onDone={() => { setEditing(null); refresh() }}
        onCancel={() => setEditing(null)}
      />
    )
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Select
          className="w-40"
          value={status}
          onChange={(event) => {
            setSearchParams(event.target.value ? { status: event.target.value } : {})
            setPage(0)
          }}
        >
          {STATUS_FILTERS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </Select>
        <Button onClick={() => setEditing('new')}>新增願望</Button>
      </div>

      {!organization.canPublishWishes && (
        <Notice tone="warning">
          機構尚未通過審核，可以先建立草稿，核准後再上架。
        </Notice>
      )}



      {wishes.isLoading && <Spinner label="載入願望" />}
      {wishes.isError && <ErrorBanner error={wishes.error} onRetry={() => void wishes.refetch()} />}

      {wishes.data?.content.length === 0 && (
        <EmptyState title="還沒有任何願望" hint="按右上角「新增願望」開始。" />
      )}

      <div className="space-y-4">
        {wishes.data?.content.map((wish) => (
          <WishRow key={wish.id} wish={wish} onEdit={() => setEditing(wish)} onChanged={refresh} />
        ))}
      </div>

      {wishes.data && <Pagination page={wishes.data} onChange={setPage} />}
    </div>
  )
}

function WishRow({ wish, onEdit, onChanged }: {
  wish: WishOrgView; onEdit: () => void; onChanged: () => void
}) {
  const action = useMutation({
    mutationFn: (path: 'publish' | 'unpublish') =>
      api.post(`/api/wishes/${wish.id}/${path}`),
    onSuccess: onChanged,
  })

  const remove = useMutation({
    mutationFn: () => api.delete(`/api/wishes/${wish.id}`),
    onSuccess: onChanged,
  })

  return (
    <div className="rounded-xl bg-white p-4 ring-1 ring-santa-100">
      <div className="flex gap-4">
        <div className="h-20 w-20 shrink-0 overflow-hidden rounded-lg bg-santa-50">
          {wish.imageUrl
            ? <img src={wish.imageUrl} alt="" className="h-full w-full object-cover" />
            : <div className="flex h-full items-center justify-center text-2xl">
                {wishIcon(wish.category)}
              </div>}
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <h3 className="font-semibold text-slate-800">{wish.title}</h3>
            <WishStatusBadge status={wish.status} />
          </div>
          <p className="mt-0.5 text-sm text-slate-500">
            {wish.childAlias}
            {wish.publishedAt && `・上架於 ${formatDate(wish.publishedAt)}`}
          </p>

          <div className="mt-3 flex flex-wrap items-center gap-2">
            {wish.status === 'AVAILABLE' ? (
              <Button variant="secondary" disabled={action.isPending}
                onClick={() => action.mutate('unpublish')}>下架</Button>
            ) : (wish.status === 'DRAFT' || wish.status === 'ARCHIVED') && (
              <Button variant="secondary" disabled={action.isPending}
                onClick={() => action.mutate('publish')}>上架</Button>
            )}

            {wish.editable && (
              <>
                <Button variant="secondary" onClick={onEdit}>編輯</Button>
                {WISH_IMAGE_ENABLED && (
                  <ImageUploader
                    purpose="WISH_IMAGE"
                    targetId={wish.id}
                    label={wish.imageUrl ? '更換示意圖' : '上傳示意圖'}
                    onUploaded={onChanged}
                  />
                )}
              </>
            )}

            {wish.deletable && (
              <Button variant="ghost" disabled={remove.isPending}
                onClick={() => remove.mutate()}>刪除</Button>
            )}
          </div>

          {action.isError && <div className="mt-3"><ErrorBanner error={action.error} /></div>}
          {remove.isError && <div className="mt-3"><ErrorBanner error={remove.error} /></div>}
        </div>
      </div>
    </div>
  )
}

function WishForm({ wish, onDone, onCancel }: {
  wish: WishOrgView | null; onDone: () => void; onCancel: () => void
}) {
  const options = useQuery({
    queryKey: ['wish-options'],
    queryFn: () => api.get<WishFilterOptions>('/api/wishes/options'),
    staleTime: Infinity,
  })

  const [form, setForm] = useState<WishRequestBody>({
    childAlias: wish?.childAlias ?? '',
    ageRange: wish?.ageRange ?? 'AGE_7_9',
    interests: wish?.interests ?? '',
    title: wish?.title ?? '',
    description: wish?.description ?? '',
    category: wish?.category ?? 'TOY',
    priceRange: wish?.priceRange ?? 'UNDER_500',
  })

  const save = useMutation({
    mutationFn: () => wish
      ? api.patch<WishOrgView>(`/api/wishes/${wish.id}`, form)
      : api.post<WishOrgView>('/api/wishes', form),
    onSuccess: onDone,
  })

  const update = (key: keyof WishRequestBody) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
  ) => setForm((current) => ({ ...current, [key]: event.target.value }))

  return (
    <form
      className="max-w-2xl space-y-5"
      onSubmit={(event) => { event.preventDefault(); save.mutate() }}
    >
      <h2 className="text-xl font-semibold text-slate-800">
        {wish ? '編輯願望' : '新增願望'}
      </h2>

      <Notice tone="warning">
        請勿填寫孩子的真實姓名、生日、學校或住址。用暱稱與年齡區間就好——
        如果一個陌生人拿著這則願望有可能認出這個孩子，就寫得太多了。
      </Notice>

      <div className="grid gap-5 sm:grid-cols-2">
        <Field label="孩子的暱稱" required hint="非真實姓名">
          <TextInput required maxLength={50} value={form.childAlias}
            placeholder="小星" onChange={update('childAlias')} />
        </Field>
        <Field label="年齡區間" required>
          <Select required value={form.ageRange} onChange={update('ageRange')}>
            {options.data?.ageRanges.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </Select>
        </Field>
      </div>

      <Field label="喜歡的東西" hint="讓捐贈者更好挑選，例如「喜歡畫畫和恐龍」">
        <TextInput maxLength={500} value={form.interests ?? ''} onChange={update('interests')} />
      </Field>

      <Field label="願望標題" required>
        <TextInput required maxLength={120} value={form.title}
          placeholder="一盒 48 色的色鉛筆" onChange={update('title')} />
      </Field>

      <Field label="願望說明">
        <TextArea rows={4} maxLength={5000} value={form.description ?? ''}
          onChange={update('description')} />
      </Field>

      <div className="grid gap-5 sm:grid-cols-2">
        <Field label="分類" required>
          <Select required value={form.category} onChange={update('category')}>
            {options.data?.categories.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </Select>
        </Field>
        <Field label="預估價格" required>
          <Select required value={form.priceRange} onChange={update('priceRange')}>
            {options.data?.priceRanges.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </Select>
        </Field>
      </div>

      {save.isError && <ErrorBanner error={save.error} />}

      <div className="flex gap-2">
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? '儲存中…' : wish ? '儲存變更' : '建立草稿'}
        </Button>
        <Button variant="ghost" onClick={onCancel}>取消</Button>
      </div>

      {!wish && (
        <p className="text-sm text-slate-500">
          建立後為草稿，回到清單即可上傳示意圖並上架。
        </p>
      )}
    </form>
  )
}
