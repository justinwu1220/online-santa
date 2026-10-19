import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { ApiError, api } from '../../lib/api'
import { useAuth } from '../../lib/authContext'
import { usePageEnter } from '../../lib/useEntryAnimation'
import { pageTitle } from '../../lib/brand'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import { reducedMotion } from '../../lib/motion'
import { isCompleteLandline, isCompleteMobile } from '../../lib/phone'
import {
  ACCEPTED_ORG_DOCUMENT_TYPES, ACCEPTED_ORG_PHOTO_TYPES, MAX_ORG_DOCUMENTS, uploadImage,
  validateOrgDocument, validateOrgPhoto,
} from '../../lib/upload'
import { isBundleCurrent, useAcceptTerms, useTermsStatus } from '../../lib/terms'
import { AlertTriangle, Check, FilePlus, ImagePlus, Mail, X } from 'lucide-react'
import type { OrganizationView } from '../../lib/types'
import { WrongAccountPanel } from '../../components/auth/WrongAccountPanel'
import { Snowfall } from '../../components/Snowfall'
import { TermsDialog } from '../../components/terms/TermsDialog'
import { ORG_TERMS } from '../../components/terms/termsContent'
import { Callout } from '../../components/ui/Callout'
import { FormField, Input, TextArea } from '../../components/ui/Fields'
import { PhoneInput } from '../../components/ui/PhoneInput'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { VerifyBar } from '../../components/ui/VerifyBar'

type Form = {
  name: string
  contactPerson: string
  contactEmail: string
  contactPhone: string
  mobilePhone: string
  address: string
  preferredDeliveryTime: string
  description: string
  website: string
}

type Draft = { form: Form; contactEmailTouched: boolean }

const EMPTY: Draft = {
  form: {
    name: '', contactPerson: '', contactEmail: '', contactPhone: '', mobilePhone: '',
    address: '', preferredDeliveryTime: '', description: '', website: '',
  },
  contactEmailTouched: false,
}

const INTRO_MAX = 100
const draftKeyFor = (email: string) => `org-apply-draft:${email}`

function readDraft(key: string): Draft {
  try {
    const saved = sessionStorage.getItem(key)
    if (!saved) return EMPTY
    const parsed = JSON.parse(saved) as Partial<Draft>
    return {
      form: { ...EMPTY.form, ...parsed.form },
      contactEmailTouched: Boolean(parsed.contactEmailTouched),
    }
  } catch {
    // 讀不到或內容壞掉就當作沒有草稿——不要讓它變成白畫面
    return EMPTY
  }
}

/**
 * 機構申請的步驟二：機構資料。
 *
 * <p>這一層只做身分分流。表單在 ApplyForm 裡，並以帳號信箱當 key——換帳號時整個重新
 * 掛載，草稿才不會跨帳號互相汙染。
 */
export function OrgApply() {
  const auth = useAuth()
  const me = useCurrentUser()

  usePageEnter()

  useEffect(() => {
    document.title = pageTitle('機構申請')
    return () => { document.title = pageTitle() }
  }, [])

  if (auth.loading) return <Shell><Spinner /></Shell>
  // 還沒有帳號：回步驟一
  if (!auth.email) return <Navigate to="/login?tab=register&as=org" replace />

  if (me.isLoading) return <Shell><Spinner label="確認身分" /></Shell>
  if (me.isError) {
    return (
      <Shell>
        <ErrorNotice error={me.error} onRetry={() => void me.refetch()} />
      </Shell>
    )
  }

  const role = effectiveRoleOf(me.data)
  if (role === 'ORG_MEMBER') return <Navigate to="/org" replace />
  if (role === 'ADMIN') {
    return (
      <Shell>
        <WrongAccountPanel
          expected="ORG_MEMBER"
          reason="平台管理員無法註冊或管理機構，這是為了避免球員兼裁判。"
        />
      </Shell>
    )
  }

  return <ApplyForm key={auth.email} email={auth.email} />
}

/**
 * 機構資料表單（設計稿 12.2、13.4）。
 *
 * <p>密碼註冊的人會在這一頁等驗證信，而在同一個分頁點驗證信連結會離開頁面——所以內容
 * 存進 sessionStorage，回來時還原。檔案無法存進 sessionStorage，離開這頁會遺失已選的檔案，
 * 已知限制。
 */
function ApplyForm({ email }: { email: string }) {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const formRef = useRef<HTMLFormElement>(null)
  const verifyRef = useRef<HTMLDivElement>(null)

  // 進度條第二段從 0 長到 100%（0.7s，SPEC 12.4）
  useEffect(() => {
    document.querySelector('.apply__bar i:last-child')?.animate(
      [{ transform: 'scaleX(0)' }, { transform: 'scaleX(1)' }],
      { duration: 700, delay: 150, easing: 'cubic-bezier(.22,.8,.3,1)', fill: 'backwards' })
  }, [])

  const draftKey = draftKeyFor(email)
  // 在初始化函式裡讀，而不是在 effect 裡 setState——後者會多一次 render，
  // 而且第一幀會閃過空白的表單
  const [draft, setDraft] = useState<Draft>(() => readDraft(draftKey))
  const { form, contactEmailTouched } = draft

  const [photo, setPhoto] = useState<{ file: File; url: string; warn: string | null } | null>(null)
  const [documents, setDocuments] = useState<File[]>([])
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [focusTick, setFocusTick] = useState(0)

  useEffect(() => {
    try {
      sessionStorage.setItem(draftKey, JSON.stringify(draft))
    } catch {
      // 存不了就算了，只是重新整理後要重打
    }
  }, [draft, draftKey])

  // 機構照片預覽：3:2 框內以 cover 顯示；直式或寬度不足時給提醒（不擋送出）。
  // 預覽網址在選檔的事件裡建立；換掉、移除或離開這頁時釋放
  const photoUrlRef = useRef<string | null>(null)
  useEffect(() => () => {
    if (photoUrlRef.current) URL.revokeObjectURL(photoUrlRef.current)
  }, [])

  function choosePhoto(file: File | null) {
    if (photoUrlRef.current) URL.revokeObjectURL(photoUrlRef.current)
    photoUrlRef.current = null
    if (!file) {
      setPhoto(null)
      return
    }
    const url = URL.createObjectURL(file)
    photoUrlRef.current = url
    setPhoto({ file, url, warn: null })
    const image = new Image()
    image.onload = () => {
      const warn = image.naturalHeight > image.naturalWidth
        ? '這是直式照片，顯示時上下會被裁掉不少，建議改用橫式'
        : image.naturalWidth < 1200
          ? `照片寬度只有 ${image.naturalWidth} 像素，放大顯示時可能不夠清楚`
          : null
      setPhoto((current) => (current && current.url === url ? { ...current, warn } : current))
    }
    image.src = url
  }

  const contactEmail = contactEmailTouched ? form.contactEmail : email

  // 一般從「選機構身分註冊」進來的人在建立帳號時已經同意《合作機構參與須知》；
  // 已經有帳號（例如原本是認領者）才進來的人還沒有，送出申請前補上
  const orgTerms = useTermsStatus(ORG_TERMS)
  const acceptOrgTerms = useAcceptTerms(ORG_TERMS)
  const [termsOpen, setTermsOpen] = useState(false)

  const register = useMutation({
    mutationFn: async () => {
      const organization = await api.post<OrganizationView>('/api/organizations', {
        ...form,
        contactEmail,
        // 空白的選填欄位不送，後端就不會把空字串存進去
        contactPhone: form.contactPhone || undefined,
        website: form.website || undefined,
      })
      // 機構在送出前沒有 id，照片與文件只能在機構建立之後才上傳。這裡若有一份
      // 上傳失敗，機構申請本身已經成立（不會、也不該回滾）——使用者會看到錯誤，
      // 但機構其實已經送出去了；機構設定頁可以補傳照片
      if (photo) await uploadImage('ORG_PHOTO', organization.id, photo.file)
      for (const file of documents) {
        await uploadImage('ORG_DOCUMENT', organization.id, file)
      }
      return organization
    },
    // 後端退回欄位錯誤時捲到第一個有問題的欄位
    onError: (error) => {
      if (error instanceof ApiError && error.fieldErrors) setFocusTick((tick) => tick + 1)
    },
    onSuccess: () => {
      try {
        sessionStorage.removeItem(draftKey)
      } catch { /* 清不掉也無所謂，下次進來會被覆寫 */ }
      // 註冊者會從 DONOR 變成 ORG_MEMBER，身分要重新載入。
      //
      // 這裡刻意不自己 navigate('/org')：身分重新載入是非同步的，馬上導過去的話
      // /org 的 RequireRole 讀到的還是舊的 DONOR，會把人彈回登入頁再彈回來。
      // 上層的角色分流看到 ORG_MEMBER 就會自動導向，等身分真的更新了才走。
      void queryClient.invalidateQueries({ queryKey: ['me'] })
      void queryClient.invalidateQueries({ queryKey: ['organization'] })
      void queryClient.invalidateQueries({ queryKey: ['organization-onboarding'] })
    },
  })

  const serverErrors = register.error instanceof ApiError ? register.error.fieldErrors : undefined
  const err = (key: string) => errors[key] ?? serverErrors?.[key]

  /** 輸入後清掉該欄的錯誤（設計稿：開始輸入就清掉）。 */
  const clearError = (key: string) =>
    setErrors((current) => {
      if (!(key in current)) return current
      const { [key]: _removed, ...rest } = current
      return rest
    })

  const update = (key: keyof Form) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    setDraft((current) => ({ ...current, form: { ...current.form, [key]: event.target.value } }))
    clearError(key)
  }

  /** 電話欄位：已經格式化好的字串直接存。 */
  const setPhone = (key: 'mobilePhone' | 'contactPhone') => (next: string) => {
    setDraft((current) => ({ ...current, form: { ...current.form, [key]: next } }))
    clearError(key)
  }

  function validate(): boolean {
    const next: Record<string, string> = {}
    const need = (key: keyof Form, label: string, value = form[key].trim()) => {
      if (!value) next[key] = `請填寫${label}`
    }
    need('name', '機構名稱')
    need('contactPerson', '承辦人姓名')
    need('contactEmail', '聯絡信箱', contactEmail.trim())
    if (contactEmail.trim() && !/^\S+@\S+\.\S+$/.test(contactEmail.trim())) next.contactEmail = '請確認信箱格式'
    need('mobilePhone', '行動電話')
    if (form.mobilePhone && !isCompleteMobile(form.mobilePhone)) next.mobilePhone = '請填寫 10 碼手機號碼，例如 0912-345-678'
    if (form.contactPhone && !isCompleteLandline(form.contactPhone)) next.contactPhone = '請確認電話號碼，例如 04-2345-6789'
    need('address', '收件地址')
    need('preferredDeliveryTime', '方便收件時間')
    need('description', '機構簡介')
    if (form.website.trim() && !/^https?:\/\/\S+\.\S+/.test(form.website.trim())) {
      next.website = '請填寫完整網址，例如 https://example.org'
    }
    if (!photo) next.photo = '請至少上傳一個檔案'
    if (documents.length === 0) next.documents = '請至少上傳一個檔案'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  // 有錯誤時捲到第一個並聚焦（設計稿 validateForm）
  useEffect(() => {
    if (focusTick === 0) return
    const first = formRef.current?.querySelector<HTMLElement>('.field.is-invalid')
    if (!first) return
    first.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'center' })
    const target = first.querySelector<HTMLElement>('input:not([type=file]), textarea')
    window.setTimeout(() => target?.focus({ preventScroll: true }), reducedMotion() ? 0 : 300)
  }, [focusTick])

  // 驗證完成：提醒框變綠、送出鈕輕微放大一次（SPEC 12.4）
  const wasVerified = useRef(auth.emailVerified)
  useEffect(() => {
    if (auth.emailVerified && !wasVerified.current && !reducedMotion()) {
      verifyRef.current?.animate(
        [{ transform: 'scale(.98)', opacity: 0.6 }, { transform: 'none', opacity: 1 }],
        { duration: 360, easing: 'cubic-bezier(.22,.8,.3,1)' })
      document.getElementById('applySubmit')?.animate(
        [{ transform: 'scale(1)' }, { transform: 'scale(1.05)' }, { transform: 'scale(1)' }],
        { duration: 420, delay: 120, easing: 'ease-out' })
    }
    wasVerified.current = auth.emailVerified
  }, [auth.emailVerified])

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!auth.emailVerified) {
      // 未驗證就按送出：驗證提醒框左右輕晃（SPEC 12.4）
      const box = verifyRef.current
      box?.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'center' })
      if (box && !reducedMotion()) {
        box.animate([
          { transform: 'translateX(0)' }, { transform: 'translateX(-6px)' }, { transform: 'translateX(6px)' },
          { transform: 'translateX(-3px)' }, { transform: 'none' },
        ], { duration: 380 })
      }
      return
    }
    if (!validate()) {
      setFocusTick((tick) => tick + 1)
      return
    }
    if (orgTerms.data && !orgTerms.data.upToDate) {
      setTermsOpen(true)
      return
    }
    register.mutate()
  }

  if (register.isSuccess) {
    return <Shell><Spinner label="申請已送出，正在進入機構後台" /></Shell>
  }

  const introLength = [...form.description].length
  const verified = auth.emailVerified

  return (
    <Shell>
      <div className="apply">
        <div className="apply__step">
          <span>步驟 2 / 2　填寫機構資料</span>
          <span className="apply__bar" aria-hidden>
            <i />
            <i className="apply__fill" />
          </span>
        </div>

        <div>
          <h1 className="apply__title">機構資料</h1>
          <p className="apply__intro">
            以 <strong>{email}</strong> 的身分申請。送出後由平台審核，核准前可以先建立願望草稿。
          </p>
          <Callout tone="warn" icon={<AlertTriangle className="icon" aria-hidden />}>
            成為機構成員後，<strong>這個帳號將無法再以個人身分認領願望</strong>——一個帳號只能有一種身分。
            如果你也想以個人身分參與，建議機構改用另一個聯絡信箱註冊。
          </Callout>

          <form ref={formRef} className="panel apply__form" noValidate onSubmit={submit}>
            <div className="form-grid">
              <FormField label="機構名稱" required span={6} error={err('name')}>
                <Input maxLength={120} placeholder="某某社會福利基金會"
                  value={form.name} onChange={update('name')} />
              </FormField>

              <FormField label="承辦人姓名" required span={3} error={err('contactPerson')}
                hint="平台審核與捐贈者聯繫時的窗口">
                <Input maxLength={100} autoComplete="name" placeholder="王小明"
                  value={form.contactPerson} onChange={update('contactPerson')} />
              </FormField>

              <FormField label="聯絡信箱" required span={3} error={err('contactEmail')}
                hint="請以機構的聯絡信箱為優先">
                <Input type="email" maxLength={255} autoComplete="email" value={contactEmail}
                  onChange={(event) => {
                    setDraft((current) => ({
                      contactEmailTouched: true,
                      form: { ...current.form, contactEmail: event.target.value },
                    }))
                    clearError('contactEmail')
                  }} />
              </FormField>

              <FormField label="行動電話" required span={3} error={err('mobilePhone')}>
                <PhoneInput kind="mobile" placeholder="0912-345-678"
                  value={form.mobilePhone} onChange={setPhone('mobilePhone')} />
              </FormField>

              <FormField label="聯絡電話" span={3} error={err('contactPhone')}>
                <PhoneInput kind="landline" placeholder="04-2345-6789"
                  value={form.contactPhone} onChange={setPhone('contactPhone')} />
              </FormField>

              <FormField label="收件地址" required span={6} error={err('address')}
                hint="捐贈者會把禮物寄到這裡，請填寫完整地址">
                <Input maxLength={255} autoComplete="street-address" placeholder="台北市中正區某某路 1 號"
                  value={form.address} onChange={update('address')} />
              </FormField>

              <FormField label="方便收件時間" required span={6} error={err('preferredDeliveryTime')}
                hint="讓捐贈者知道什麼時候送比較方便，例如「平日上午 9:00–下午 5:00」">
                <Input maxLength={255} placeholder="平日上午 9:00–下午 5:00"
                  value={form.preferredDeliveryTime} onChange={update('preferredDeliveryTime')} />
              </FormField>

              <FormField label="機構簡介" required span={6} error={err('description')}
                hint="100 字以內，會顯示在願望詳細頁，讓捐贈者了解你們服務的對象、平常在做什麼">
                <TextArea rows={4} maxLength={INTRO_MAX} aria-describedby="aIntroCount"
                  placeholder="例：我們陪伴因家庭因素暫時無法與家人同住的 6–18 歲孩子，提供住宿、課後陪讀與生活照顧，也帶孩子練習自己打理生活。今年第一次參加線上聖誕老人，希望每個孩子都收到一份專屬的禮物。"
                  value={form.description} onChange={update('description')} />
                <span id="aIntroCount" className={`char-count${introLength >= INTRO_MAX ? ' is-full' : ''}`}
                  aria-live="polite">{introLength} / {INTRO_MAX}</span>
              </FormField>

              <FormField label="機構官方網站" span={6} error={err('website')}
                hint="願望詳細頁的「前往機構官方網站」按鈕">
                <Input type="url" maxLength={255} placeholder="https://"
                  value={form.website} onChange={update('website')} />
              </FormField>

              {/* 外框用 div：label 會把範圍內任何點擊轉給裡面的 file input */}
              <FormField as="div" label="機構照片" required span={6} error={err('photo')}
                hint={<>橫式 3:2，建議 1200 × 800 像素以上，JPG 或 PNG。重要的人事物放在畫面中間（手機與側欄會裁成較扁的比例）；請避免<span className="nobr">拍到孩子的臉。</span></>}>
                <div className="photo-pick">
                  <div className={`photo-preview${photo ? ' has-img' : ''}`} aria-hidden>
                    {photo && <img src={photo.url} alt="" />}
                    <span>3:2 橫式</span>
                    <i className="photo-preview__safe" />
                  </div>
                  <div className="upload">
                    <label className="upload__btn">
                      <ImagePlus className="icon" aria-hidden />
                      新增照片
                      <input type="file" accept={ACCEPTED_ORG_PHOTO_TYPES.join(',')}
                        aria-label="機構照片"
                        onChange={(event) => {
                          const file = event.target.files?.[0]
                          event.target.value = ''
                          if (!file) return
                          const problem = validateOrgPhoto(file)
                          if (problem) {
                            setErrors((current) => ({ ...current, photo: problem }))
                            return
                          }
                          choosePhoto(file)
                          clearError('photo')
                        }} />
                    </label>
                    {photo && <FileChip name={photo.file.name} onRemove={() => choosePhoto(null)} />}
                  </div>
                </div>
                {photo?.warn && <span className="photo-warn">{photo.warn}</span>}
              </FormField>

              <FormField as="div" label="相關文件證明" required span={6} error={err('documents')}
                hint="請擇一上傳：法人登記證書／立案證書／設立許可函／教職員證／當學年度聘書">
                <div className="upload">
                  {documents.length < MAX_ORG_DOCUMENTS && (
                    <label className="upload__btn">
                      <FilePlus className="icon" aria-hidden />
                      新增文件
                      <input type="file" multiple aria-label="相關文件證明"
                        accept={ACCEPTED_ORG_DOCUMENT_TYPES.join(',')}
                        onChange={(event) => {
                          const files = [...(event.target.files ?? [])]
                          event.target.value = ''
                          const accepted: File[] = []
                          for (const file of files) {
                            if (documents.length + accepted.length >= MAX_ORG_DOCUMENTS) {
                              setErrors((current) => ({ ...current, documents: `最多上傳 ${MAX_ORG_DOCUMENTS} 份文件` }))
                              break
                            }
                            const problem = validateOrgDocument(file)
                            if (problem) {
                              setErrors((current) => ({ ...current, documents: problem }))
                              break
                            }
                            accepted.push(file)
                            clearError('documents')
                          }
                          if (accepted.length) setDocuments((current) => [...current, ...accepted])
                        }} />
                    </label>
                  )}
                  {documents.map((file, index) => (
                    <FileChip key={`${file.name}-${index}`} name={file.name}
                      onRemove={() => setDocuments((current) => current.filter((_, i) => i !== index))} />
                  ))}
                </div>
              </FormField>
            </div>

            <div ref={verifyRef}>
              {verified ? (
                <Callout tone="ok" className="apply__verify" icon={<Check className="icon" aria-hidden />}>
                  <strong>信箱已驗證，</strong>確認資料無誤後就可以送出申請。
                </Callout>
              ) : (
                <Callout tone="warn" className="apply__verify" icon={<Mail className="icon" aria-hidden />}>
                  <strong>送出前要先完成信箱驗證。</strong>驗證信寄到 <strong>{email}</strong>，點完連結後回到這一頁，
                  按上方的「我已經驗證好了」就能送出。你填的資料會留著，離開這一頁也不會消失。
                </Callout>
              )}
            </div>

            {register.isError && !serverErrors && (
              <div style={{ marginTop: 16 }}><ErrorNotice error={register.error} /></div>
            )}

            <div className="form-actions">
              <button id="applySubmit" type="submit" className="btn btn--primary"
                disabled={register.isPending || !verified}>
                {register.isPending ? '送出中…' : '送出申請'}
              </button>
            </div>
          </form>
        </div>
      </div>

      <TermsDialog
        open={termsOpen}
        onClose={() => setTermsOpen(false)}
        terms={ORG_TERMS}
        mode="consent"
        acceptLabel="同意並送出申請"
        outdated={!isBundleCurrent(orgTerms.data, ORG_TERMS)}
        onAccept={async () => {
          await acceptOrgTerms()
          setTermsOpen(false)
          register.mutate()
        }}
      />
    </Shell>
  )
}

/** 已選檔案的標籤，可移除（設計稿 `.chip`）。 */
function FileChip({ name, onRemove }: { name: string; onRemove: () => void }) {
  return (
    <span className="chip">
      <span>{name}</span>
      <button type="button" aria-label={`移除 ${name.replace(/"/g, '')}`} onClick={onRemove}>
        <X className="icon" aria-hidden />
      </button>
    </span>
  )
}

/** 這一頁不在任何 layout 底下，深色背景、飄雪與驗證提示列要自己帶。 */
function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="rd">
      <Snowfall />
      <VerifyBar />
      <main className="page">{children}</main>
    </div>
  )
}
