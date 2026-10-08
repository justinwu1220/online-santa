import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, withQuery } from '../../lib/api'
import { formatDate } from '../../lib/format'
import type {
  AttachmentView, PageResponse, WishFilterOptions, WishOrgView, WishRequestBody, WishStatus,
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
  const year = searchParams.get('year') ?? ''
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<WishOrgView | 'new' | null>(null)

  // 願望管理頁沒有像機構總覽那樣每頁都會打的統計端點可以搭便車，
  // 另開一支輕量端點，寫法比照既有的 /api/wishes/options
  const years = useQuery({
    queryKey: ['org-wishes', 'years'],
    queryFn: () => api.get<number[]>('/api/organizations/me/wishes/years'),
    staleTime: 30_000,
  })

  const wishes = useQuery({
    queryKey: ['org-wishes', status, year, page],
    queryFn: () => api.get<PageResponse<WishOrgView>>(
      withQuery('/api/organizations/me/wishes', { status, year: year || undefined, page, size: 10 })),
  })

  /** status／year 各自獨立存在網址上，換一個篩選不該把另一個洗掉。 */
  const setFilter = (key: 'status' | 'year', value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    setSearchParams(next)
    setPage(0)
  }

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
        <div className="flex items-center gap-2">
          <Select
            className="w-40"
            value={status}
            onChange={(event) => setFilter('status', event.target.value)}
          >
            {STATUS_FILTERS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </Select>
          <Select
            className="w-28"
            value={year}
            onChange={(event) => setFilter('year', event.target.value)}
          >
            <option value="">全部年度</option>
            {(years.data ?? []).map((y) => (
              <option key={y} value={y}>{y}</option>
            ))}
          </Select>
        </div>
        <Button disabled={!organization.canDraftWishes} onClick={() => setEditing('new')}>
          新增願望
        </Button>
      </div>

      {!organization.canPublishWishes && (
        <Notice tone="warning">
          {organization.canDraftWishes
            ? '機構尚未通過審核，可以先建立草稿，核准後再一鍵上架。'
            : '機構已停權，無法建立或上架願望，請與平台管理員聯繫。'}
        </Notice>
      )}



      {wishes.isLoading && <Spinner label="載入願望" />}
      {wishes.isError && <ErrorBanner error={wishes.error} onRetry={() => void wishes.refetch()} />}

      {wishes.data?.content.length === 0 && (
        <EmptyState title="還沒有任何願望" hint="按右上角「新增願望」開始。" />
      )}

      <div className="space-y-4">
        {wishes.data?.content.map((wish) => (
          <WishRow key={wish.id} wish={wish} canPublish={organization.canPublishWishes}
            onEdit={() => setEditing(wish)} onChanged={refresh} />
        ))}
      </div>

      {wishes.data && <Pagination page={wishes.data} onChange={setPage} />}
    </div>
  )
}

function WishRow({ wish, canPublish, onEdit, onChanged }: {
  wish: WishOrgView
  /** 機構通過審核前，草稿建得了但上不了架——按鈕要停用而不是送出去拿 409 */
  canPublish: boolean
  onEdit: () => void
  onChanged: () => void
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
              // 未核准時停用而非隱藏：機構看得到「上架」在哪裡，只是還不能按，
              // 比按鈕憑空消失更好理解
              <Button variant="secondary"
                disabled={action.isPending || !canPublish}
                title={canPublish ? undefined : '機構通過審核後才能上架'}
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

          <LetterPhotosSection wish={wish} onChanged={onChanged} />
        </div>
      </div>
    </div>
  )
}

/**
 * 願望信件（孩童手寫的感謝卡／願望信照片）。
 *
 * 刻意不放進 `wish.editable` 的判斷裡——感謝卡通常是禮物寄出/收到之後才有，
 * 那時候願望早就不是可編輯狀態了，但機構還是要能補上這些照片。
 */
function LetterPhotosSection({ wish, onChanged }: { wish: WishOrgView; onChanged: () => void }) {
  return (
    <div className="mt-3 border-t border-slate-100 pt-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 className="text-sm font-medium text-slate-600">願望信件</h4>
        <ImageUploader
          purpose="WISH_LETTER"
          targetId={wish.id}
          label="上傳願望信件照片"
          onUploaded={onChanged}
        />
      </div>

      {wish.letterPhotos.length > 0 ? (
        <div className="mt-2 grid grid-cols-4 gap-2 sm:grid-cols-6">
          {wish.letterPhotos.map((photo) => (
            <LetterPhoto key={photo.id} photo={photo} onDeleted={onChanged} />
          ))}
        </div>
      ) : (
        <p className="mt-2 text-sm text-slate-400">還沒有上傳願望信件照片。</p>
      )}

      <div className="mt-2">
        <Notice tone="warning">請拍卡片內容，避免拍到孩子的臉部或完整姓名。</Notice>
      </div>
    </div>
  )
}

function LetterPhoto({ photo, onDeleted }: { photo: AttachmentView; onDeleted: () => void }) {
  const [confirming, setConfirming] = useState(false)

  const remove = useMutation({
    mutationFn: () => api.delete(`/api/attachments/${photo.id}`),
    onSuccess: onDeleted,
  })

  return (
    <div className="group relative overflow-hidden rounded-lg ring-1 ring-slate-200">
      <a href={photo.url} target="_blank" rel="noreferrer">
        <img src={photo.url} alt=""
          className="aspect-square w-full object-cover transition-opacity group-hover:opacity-75" />
      </a>

      {confirming ? (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-1.5
          bg-black/85 p-2 text-center">
          <p className="text-xs text-slate-200">刪除後無法復原</p>
          {remove.isError && <p className="text-xs text-berry-300">刪除失敗，請再試一次</p>}
          <div className="flex gap-1.5">
            <Button variant="danger" disabled={remove.isPending}
              onClick={() => remove.mutate()} className="px-2 py-1 text-xs">
              {remove.isPending ? '刪除中…' : '確定刪除'}
            </Button>
            <Button variant="ghost" onClick={() => setConfirming(false)} className="px-2 py-1 text-xs">
              取消
            </Button>
          </div>
        </div>
      ) : (
        <button type="button" onClick={() => setConfirming(true)}
          className="absolute right-1 top-1 rounded-md bg-black/60 px-1.5 py-0.5 text-xs
            text-white opacity-0 transition-opacity hover:bg-berry-600 group-hover:opacity-100">
          刪除
        </button>
      )}
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
    priceRange: wish?.priceRange,
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
        <Field label="孩子的暱稱" required inlineHint hint="非真實姓名">
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

      <Field label="願望標題" required>
        <TextInput required maxLength={120} value={form.title}
          placeholder="一盒 48 色的色鉛筆" onChange={update('title')} />
      </Field>

      <Field label="願望說明" inlineHint
        hint="可補充顏色、尺寸(衣物鞋子請註明)、款式偏好或購買連結，認領者會更好買">
        <TextArea rows={4} maxLength={5000} value={form.description ?? ''}
          onChange={update('description')} />
      </Field>

      <Field label="分類" required>
        <Select required value={form.category} onChange={update('category')}>
          {options.data?.categories.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </Select>
      </Field>

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
