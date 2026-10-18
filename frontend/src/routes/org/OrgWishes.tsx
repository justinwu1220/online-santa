import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, withQuery } from '../../lib/api'
import { formatDate } from '../../lib/format'
import { ACCEPTED_IMAGE_TYPES, MAX_WISH_LETTERS, uploadImage, validateImage } from '../../lib/upload'
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
  { value: 'PENDING_REVIEW', label: '審核中' },
  { value: 'AVAILABLE', label: '可認領' },
  { value: 'CLAIMED', label: '已被認領' },
  { value: 'FULFILLED', label: '已完成' },
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
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const action = useMutation({
    mutationFn: (path: 'submit' | 'withdraw') =>
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
          {wish.status === 'DRAFT' && wish.reviewNote && (
            <p className="mt-1 text-sm text-berry-600">管理員退回：{wish.reviewNote}</p>
          )}

          <div className="mt-3 flex flex-wrap items-center gap-2">
            {wish.status === 'PENDING_REVIEW' ? (
              <Button variant="secondary" disabled={action.isPending}
                onClick={() => action.mutate('withdraw')}>撤回審核</Button>
            ) : wish.status === 'DRAFT' && (
              // 未核准時停用而非隱藏：機構看得到「提交審核」在哪裡，只是還不能按，
              // 比按鈕憑空消失更好理解
              <Button variant="secondary"
                disabled={action.isPending || !canPublish}
                title={canPublish ? undefined : '機構資料審核通過後才能提交願望審核'}
                onClick={() => action.mutate('submit')}>提交審核</Button>
            )}
            {action.isError && <span className="text-sm text-berry-600">{action.error.message}</span>}

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
              confirmingDelete ? (
                <span className="flex items-center gap-2 text-sm">
                  {remove.isError && <span className="text-berry-600">刪除失敗，請再試一次</span>}
                  <Button variant="danger" disabled={remove.isPending}
                    onClick={() => remove.mutate()}>
                    {remove.isPending ? '刪除中…' : '確定刪除'}
                  </Button>
                  <Button variant="ghost" onClick={() => setConfirmingDelete(false)}>取消</Button>
                </span>
              ) : (
                <Button variant="ghost" onClick={() => setConfirmingDelete(true)}>刪除</Button>
              )
            )}
          </div>

          {action.isError && <div className="mt-3"><ErrorBanner error={action.error} /></div>}
        </div>
      </div>
    </div>
  )
}

/**
 * 已存在（已確認上傳）的願望信件照片縮圖格。
 *
 * 點「移除」只是在本機把這張照片標記為「這次編輯要移除」，不會立刻打 API
 * ——跟新增的照片同一套邏輯，要等整個表單按下「儲存」才真的呼叫
 * DELETE，按「取消」的話這個標記直接隨元件卸載作廢，照片原封不動。正因為
 * 不是立即、不可逆的動作，這裡不需要像刪除已確認資料那樣另外加一層確認
 * 對話框，點一下直接標記即可。
 */
function ExistingLetterPhotos({ photos, onRemove }: {
  photos: AttachmentView[]
  onRemove: (photoId: string) => void
}) {
  if (photos.length === 0) return null

  return (
    <div className="mb-2 grid grid-cols-4 gap-2 sm:grid-cols-6">
      {photos.map((photo) => (
        <div key={photo.id}
          className="group relative overflow-hidden rounded-lg ring-1 ring-slate-200">
          <a href={photo.url} target="_blank" rel="noreferrer">
            <img src={photo.url} alt=""
              className="aspect-square w-full object-cover transition-opacity group-hover:opacity-75" />
          </a>
          <button type="button" onClick={() => onRemove(photo.id)}
            title="儲存變更後才會正式刪除"
            className="absolute right-1 top-1 rounded-md bg-black/60 px-1.5 py-0.5 text-xs
              text-white opacity-0 transition-opacity hover:bg-berry-600 group-hover:opacity-100">
            移除
          </button>
        </div>
      ))}
    </div>
  )
}

function WishForm({ wish, onDone, onCancel }: {
  wish: WishOrgView | null
  onDone: () => void
  onCancel: () => void
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

  // 願望信件照片一律先在本機暫存／標記，按「儲存」才真的呼叫 API——新增
  // 願望時還沒有 id，本來就只能這樣；編輯既有願望時也改成同一套，不要在
  // 選檔案或點刪除的那一刻就直接打 API：使用者操作完卻按「取消」，不該讓
  // 伺服器端已經悄悄發生變化。existingLetterPhotos 是打開表單那一刻的
  // 靜態快照，removedPhotoIds 記錄這次編輯要移除的既有照片 id，兩者合起來
  // 算出目前「看得到」的既有照片，而不是直接改動快照本身
  const existingLetterPhotos = wish?.letterPhotos ?? []
  const [removedPhotoIds, setRemovedPhotoIds] = useState<Set<string>>(new Set())
  const [letterPhotos, setLetterPhotos] = useState<File[]>([])
  const [letterPhotoError, setLetterPhotoError] = useState<string | null>(null)
  const letterPhotoInputRef = useRef<HTMLInputElement>(null)
  const visibleExistingPhotos = existingLetterPhotos.filter((p) => !removedPhotoIds.has(p.id))
  const totalLetterPhotos = visibleExistingPhotos.length + letterPhotos.length

  const save = useMutation({
    mutationFn: async () => {
      const saved = wish
        ? await api.patch<WishOrgView>(`/api/wishes/${wish.id}`, form)
        : await api.post<WishOrgView>('/api/wishes', form)
      // 已知限制：願望存檔成功後，若刪除舊照片或上傳新照片失敗，願望本身
      // 不會被撤銷（也不該為了這類失敗撤銷整張草稿），使用者會看到錯誤，
      // 但願望其實已經存檔了，這點不在這次處理範圍內
      for (const photoId of removedPhotoIds) {
        await api.delete(`/api/attachments/${photoId}`)
      }
      for (const file of letterPhotos) {
        await uploadImage('WISH_LETTER', saved.id, file)
      }
      return saved
    },
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

      <Field label="願望信件照片" inlineHint hint="孩子的手寫願望卡片"
        error={letterPhotoError ?? undefined}>
        <ExistingLetterPhotos photos={visibleExistingPhotos} onRemove={(photoId) => {
          setRemovedPhotoIds((current) => new Set(current).add(photoId))
        }} />
        <input
          ref={letterPhotoInputRef}
          type="file"
          className="hidden"
          accept={ACCEPTED_IMAGE_TYPES.join(',')}
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (!file) return
            if (totalLetterPhotos >= MAX_WISH_LETTERS) {
              setLetterPhotoError(`最多上傳 ${MAX_WISH_LETTERS} 張照片`)
            } else {
              const problem = validateImage(file)
              if (problem) {
                setLetterPhotoError(problem)
              } else {
                setLetterPhotos((current) => [...current, file])
                setLetterPhotoError(null)
              }
            }
            event.target.value = ''
          }}
        />
        <div className="space-y-2">
          {letterPhotos.map((file, index) => (
            <div key={`${file.name}-${index}`}
              className="flex items-center justify-between gap-2 rounded-lg border
                border-slate-200 bg-white px-3 py-2 text-sm">
              <span className="truncate text-slate-700">{file.name}</span>
              <button type="button" className="text-slate-400 hover:text-berry-600"
                onClick={() => setLetterPhotos((current) => current.filter((_, i) => i !== index))}>
                移除
              </button>
            </div>
          ))}
          {totalLetterPhotos < MAX_WISH_LETTERS && (
            <Button type="button" variant="secondary"
              onClick={() => letterPhotoInputRef.current?.click()}>
              新增照片
            </Button>
          )}
        </div>
        <div className="mt-2">
          <Notice tone="warning">請拍卡片內容，避免拍到孩子的臉部或完整姓名。</Notice>
        </div>
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
