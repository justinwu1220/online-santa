import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, Mail } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Callout } from '../../components/ui/Callout'
import { Drawer } from '../../components/console/Drawer'
import { useToast } from '../../components/ui/toastContext'
import { ApiError, api } from '../../lib/api'
import { reducedMotion } from '../../lib/motion'
import type { WishFilterOptions, WishOrgView } from '../../lib/types'
import { uploadImage, validateImage } from '../../lib/upload'
import { useInvalidateOrg } from './orgQueries'
import { useOrgContext } from './orgContext'

const NOTE_MAX = 20

interface FormState {
  nick: string
  age: string
  title: string
  category: string
  note: string
}

const EMPTY: FormState = { nick: '', age: '', title: '', category: '', note: '' }

/**
 * 新增／編輯願望（設計稿 15.5）。草稿只需要願望標題；提交審核要全部必填
 * （暱稱、年齡區間、標題、孩子的手寫信件照片）。機構不能直接上架：提交後狀態變「審核中」，
 * 由管理員核准後才出現在願望牆。上架後不能再修改。
 *
 * 禮物分類是設計稿漏畫、保留給後台的選填欄位。
 */
export function OrgWishFormDrawer({ open, wish, onClose, onSaved }: {
  open: boolean
  wish: WishOrgView | null
  onClose: () => void
  onSaved: () => void
}) {
  const { organization } = useOrgContext()
  const invalidate = useInvalidateOrg()
  const toast = useToast()
  const formRef = useRef<HTMLFormElement>(null)
  const options = useQuery({
    queryKey: ['wish-options'],
    queryFn: () => api.get<WishFilterOptions>('/api/wishes/options'),
    staleTime: Infinity,
  })

  const [values, setValues] = useState<FormState>(EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [letter, setLetter] = useState<{ file: File; url: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [focusTick, setFocusTick] = useState(0)

  // 每次開啟重設表單（編輯時帶入既有資料）。在 render 期間依 props 的變化調整 state，
  // 不用 effect——那樣會多一次 render，第一幀還會閃過上一次的內容
  const [lastOpen, setLastOpen] = useState(false)
  if (open !== lastOpen) {
    setLastOpen(open)
    if (open) {
      setValues(wish ? {
        nick: wish.childAlias ?? '',
        age: wish.ageRange ?? '',
        title: wish.title,
        category: wish.category ?? '',
        note: wish.description ?? '',
      } : EMPTY)
      setErrors({})
      if (letter) URL.revokeObjectURL(letter.url)
      setLetter(null)
    }
  }

  const existingLetter = wish?.letterPhotos?.[0]?.url
  const previewUrl = letter?.url ?? existingLetter
  const pending = organization.status !== 'APPROVED'

  const set = (key: keyof FormState) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
  ) => {
    setValues((current) => ({ ...current, [key]: event.target.value }))
    setErrors((current) => { const { [key]: _gone, ...rest } = current; return rest })
  }

  useEffect(() => {
    if (focusTick === 0) return
    const first = formRef.current?.querySelector<HTMLElement>('.field.is-invalid')
    if (!first) return
    first.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'center' })
    window.setTimeout(() => first.querySelector<HTMLElement>('input:not([type=file]), textarea, select')
      ?.focus({ preventScroll: true }), reducedMotion() ? 0 : 300)
  }, [focusTick])

  function validate(forSubmit: boolean): boolean {
    const next: Record<string, string> = {}
    if (!values.title.trim()) {
      next.title = forSubmit ? '請填寫願望標題' : '存成草稿也要先填想要的禮物'
    }
    if (forSubmit) {
      if (!values.nick.trim()) next.nick = '請填寫孩子的暱稱'
      if (!values.age) next.age = '請填寫年齡區間'
      if (!letter && !existingLetter) next.letter = '請至少上傳一個檔案'
    }
    setErrors(next)
    if (Object.keys(next).length) setFocusTick((tick) => tick + 1)
    return Object.keys(next).length === 0
  }

  function chooseLetter(file: File | undefined) {
    if (!file) return
    // 只收圖片（後端也會檢查），選到非圖片時欄位顯示「請選擇圖片檔」
    if (validateImage(file) && !file.type.startsWith('image/')) {
      setErrors((current) => ({ ...current, letter: '請選擇圖片檔' }))
      return
    }
    const problem = validateImage(file)
    if (problem) {
      setErrors((current) => ({ ...current, letter: problem }))
      return
    }
    setLetter((current) => { if (current) URL.revokeObjectURL(current.url); return { file, url: URL.createObjectURL(file) } })
    setErrors((current) => { const { letter: _gone, ...rest } = current; return rest })
  }

  async function save(submit: boolean) {
    if (!validate(submit) || busy) return
    setBusy(true)
    try {
      const body = {
        childAlias: values.nick.trim() || undefined,
        ageRange: values.age || undefined,
        title: values.title.trim(),
        description: values.note.trim() || undefined,
        category: values.category || undefined,
        // 設計稿的表單沒有「興趣」欄位；編輯時保留舊願望原有的，不要被清掉
        interests: wish?.interests,
      }
      const saved = wish
        ? await api.patch<WishOrgView>(`/api/wishes/${wish.id}`, body)
        : await api.post<WishOrgView>('/api/wishes', body)
      if (letter) await uploadImage('WISH_LETTER', saved.id, letter.file)
      if (submit) await api.post(`/api/wishes/${saved.id}/submit`)
      await invalidate()
      toast.show(submit
        ? `「${values.title.trim()}」已提交審核，通過後會自動上架`
        : `「${values.title.trim()}」已存成草稿`)
      onSaved()
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors) {
        const map: Record<string, string> = { childAlias: 'nick', ageRange: 'age', description: 'note' }
        setErrors(Object.fromEntries(Object.entries(error.fieldErrors)
          .map(([key, message]) => [map[key] ?? key, message])))
        setFocusTick((tick) => tick + 1)
      } else {
        toast.show(error instanceof ApiError ? error.detail : '儲存失敗，請稍後再試', 'danger')
      }
    } finally {
      setBusy(false)
    }
  }

  const noteLength = [...values.note].length

  return (
    <Drawer open={open} onClose={onClose} dismissible={!busy} labelledBy="ofTitle"
      title={wish ? '編輯願望' : '新增願望'}
      foot={(
        <div className="drawer__foot">
          <button type="button" className="btn" disabled={busy} onClick={() => void save(false)}>存成草稿</button>
          <button type="button" className="btn btn--primary" disabled={pending || busy}
            onClick={() => void save(true)}>提交審核</button>
        </div>
      )}>
      <form className="drawer__body" ref={formRef} noValidate
        onSubmit={(event) => { event.preventDefault(); void save(true) }}>
        <div className="form-grid">
          <Field cls="c-3" label="孩子的暱稱" required opt="非真實姓名" error={errors.nick}>
            <input className="input" placeholder="小星" maxLength={50} value={values.nick} onChange={set('nick')} />
          </Field>
          <Field cls="c-3" label="年齡區間" required error={errors.age}>
            <select className="input" value={values.age} onChange={set('age')}>
              <option value="">請選擇</option>
              {options.data?.ageRanges.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </Field>
          <Field cls="c-6" label="願望標題" required error={errors.title}>
            <input className="input" placeholder="一盒 48 色的色鉛筆" maxLength={120}
              value={values.title} onChange={set('title')} />
          </Field>
          <Field cls="c-6" label="禮物分類" opt="選填" error={errors.category}>
            <select className="input" value={values.category} onChange={set('category')}>
              <option value="">不分類</option>
              {options.data?.categories.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </Field>
          <Field cls="c-6" label="願望說明" error={errors.note}
            hint={<>請用孩子的口吻寫，像孩子親口說的一句話（「我想要…」「因為…」），認領者讀起來會更有溫度。20 字以內，顯示在願望詳細頁的標題下方；衣物鞋子請一併寫上<span className="nobr">尺寸或顏色。</span></>}>
            <textarea className="input" rows={2} maxLength={NOTE_MAX} aria-describedby="wNoteCount"
              placeholder="例：我想要藍色的，跑步才會像風一樣快！" value={values.note} onChange={set('note')} />
            <span id="wNoteCount" className={`char-count${noteLength >= NOTE_MAX ? ' is-full' : ''}`}
              aria-live="polite">{noteLength} / {NOTE_MAX}</span>
          </Field>
          <div className={`field c-6${errors.letter ? ' is-invalid' : ''}`}>
            <span className="field__label" id="wLetterLabel">
              願望信件照片<span className="req" aria-hidden>*</span><span className="opt">孩子的手寫願望卡片</span>
            </span>
            <span className="field__hint">直式、橫式都可以，拍攝時讓卡片佔滿畫面、光線充足，<span className="nobr">文字才看得清楚。</span></span>
            <div className="letter-pick">
              <div className="letter-preview" aria-hidden>
                {previewUrl ? <img src={previewUrl} alt="" /> : <>願望詳細頁<br />顯示效果</>}
              </div>
              <div className="upload">
                <label className="upload__btn">
                  <Mail className="icon" aria-hidden />
                  新增照片
                  <input type="file" accept="image/*" aria-labelledby="wLetterLabel"
                    onChange={(event) => { const f = event.target.files?.[0]; event.target.value = ''; chooseLetter(f) }} />
                </label>
                <span className="upload__list">
                  {(letter || existingLetter) && (
                    <span className="chip">
                      <span>{letter?.file.name ?? '孩子的信件'}</span>
                      {letter && (
                        <button type="button" aria-label="移除這張照片"
                          onClick={() => setLetter((current) => { if (current) URL.revokeObjectURL(current.url); return null })}>×</button>
                      )}
                    </span>
                  )}
                </span>
              </div>
            </div>
            {errors.letter && <span className="field__error" role="alert">{errors.letter}</span>}
          </div>
        </div>
        <Callout tone="warn" icon={<AlertTriangle className="icon" aria-hidden />}>
          請拍卡片內容，避免拍到孩子的臉部或完整姓名。
        </Callout>
        <p className="auth-note of-note">
          {pending
            ? '機構資料審核中，審核通過後才能提交願望；現在可以先存成草稿。'
            : '提交後由管理員審核，通過後才會出現在願望牆。上架後就不能再修改。'}
        </p>
      </form>
    </Drawer>
  )
}

/** 欄位外框（設計稿 `.field`）：標籤、必填星號、選填註記、說明、錯誤。 */
function Field({ cls, label, required, opt, hint, error, children }: {
  cls: string
  label: string
  required?: boolean
  opt?: string
  hint?: React.ReactNode
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className={`field ${cls}${error ? ' is-invalid' : ''}`}>
      <label className="field__label">
        {label}
        {required && <span className="req" aria-hidden>*</span>}
        {opt && <span className="opt">{opt}</span>}
      </label>
      {hint && <span className="field__hint">{hint}</span>}
      {children}
      {error && <span className="field__error" role="alert">{error}</span>}
    </div>
  )
}
