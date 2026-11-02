import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FilePlus, ImagePlus, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { AttachmentGallery, PreviewImage } from '../../components/console/ImageViewer'
import { FormField, Input, SelectBox, TextArea } from '../../components/ui/Fields'
import { ImageCropDialog } from '../../components/ui/ImageCropDialog'
import { PhoneInput } from '../../components/ui/PhoneInput'
import { digitsOnly, isValidTaxId } from '../../lib/taxId'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { useToast } from '../../components/ui/toastContext'
import { ApiError, api } from '../../lib/api'
import { pageTitle } from '../../lib/brand'
import { EASE_OUT, reducedMotion } from '../../lib/motion'
import { isCompleteLandline, isCompleteMobile } from '../../lib/phone'
import type { AttachmentView, OrganizationView, ReleasePolicy, UnlockRequestView } from '../../lib/types'
import {
  ACCEPTED_ORG_DOCUMENT_TYPES, ACCEPTED_ORG_PHOTO_TYPES, MAX_ORG_DOCUMENTS, uploadImage,
  validateOrgDocument, validateOrgPhoto,
} from '../../lib/upload'
import { useOrgContext } from './orgContext'

const INTRO_MAX = 100
const GRACE_DAYS = [1, 3, 5, 7]

/**
 * 機構設定（設計稿 15.7）：機構資料、收件資訊、逾期未寄送的處理三張面板，
 * 右下固定一顆「儲存變更」。沒有通知設定——系統的提醒規則是固定的。
 * 設定變更只影響之後的新認領。
 */
export function OrgSettings() {
  const { organization } = useOrgContext()
  const queryClient = useQueryClient()
  const toast = useToast()
  const formRef = useRef<HTMLFormElement>(null)

  const [form, setForm] = useState({
    name: organization.name,
    contactPerson: organization.contactPerson ?? '',
    // 這兩欄上線前核准的機構還沒有值：要補齊才存得了設定
    responsiblePerson: organization.responsiblePerson ?? '',
    withholdingTaxId: organization.withholdingTaxId ?? '',
    contactEmail: organization.contactEmail,
    contactPhone: organization.contactPhone ?? '',
    mobilePhone: organization.mobilePhone ?? '',
    address: organization.address ?? '',
    preferredDeliveryTime: organization.preferredDeliveryTime ?? '',
    description: organization.description ?? '',
    website: organization.website ?? '',
    releasePolicy: organization.releasePolicy,
    releaseAfterDays: organization.releaseAfterDays ?? 3,
  })
  const [photo, setPhoto] = useState<{ file: File; url: string; warn: string | null } | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [focusTick, setFocusTick] = useState(0)
  const photoUrlRef = useRef<string | null>(null)
  // 剛選的照片先進裁切，確定後才成為待上傳的機構照片（原圖不會上傳）
  const [cropping, setCropping] = useState<File | null>(null)
  // 使用者主動切到「自動收回」才播寬限天數列的浮下動畫（一進頁面就是自動收回時不播）
  const graceAnimate = useRef(false)

  useEffect(() => {
    document.title = pageTitle('機構設定｜機構後台')
    return () => {
      document.title = pageTitle()
      if (photoUrlRef.current) URL.revokeObjectURL(photoUrlRef.current)
    }
  }, [])

  useEffect(() => {
    if (focusTick === 0) return
    const first = formRef.current?.querySelector<HTMLElement>('.field.is-invalid')
    if (!first) return
    first.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'center' })
    window.setTimeout(() => first.querySelector<HTMLElement>('input:not([type=file]), textarea, select')
      ?.focus({ preventScroll: true }), reducedMotion() ? 0 : 300)
  }, [focusTick])

  const clearError = (key: string) =>
    setErrors((current) => { const { [key]: _gone, ...rest } = current; return rest })

  const update = (key: keyof typeof form) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    setForm((current) => ({ ...current, [key]: event.target.value }))
    clearError(key)
  }
  const setPhone = (key: 'mobilePhone' | 'contactPhone') => (value: string) => {
    setForm((current) => ({ ...current, [key]: value }))
    clearError(key)
  }

  function choosePhoto(file: File) {
    const problem = validateOrgPhoto(file)
    if (problem) {
      setErrors((current) => ({ ...current, photo: problem }))
      return
    }
    setCropping(file)
  }

  function applyPhoto(file: File) {
    if (photoUrlRef.current) URL.revokeObjectURL(photoUrlRef.current)
    const url = URL.createObjectURL(file)
    photoUrlRef.current = url
    setPhoto({ file, url, warn: null })
    clearError('photo')
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

  const save = useMutation({
    mutationFn: async () => {
      const saved = await api.patch<OrganizationView>('/api/organizations/me', {
        ...form,
        contactPhone: form.contactPhone || undefined,
        website: form.website.trim() || undefined,
        // MANUAL 政策不該帶天數，後端與資料庫的約束都會擋
        releaseAfterDays: form.releasePolicy === 'AUTO' ? form.releaseAfterDays : undefined,
      })
      if (photo) await uploadImage('ORG_PHOTO', organization.id, photo.file)
      return saved
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['organization'] })
      if (photoUrlRef.current) URL.revokeObjectURL(photoUrlRef.current)
      photoUrlRef.current = null
      setPhoto(null)
      toast.show('機構設定已儲存')
    },
    onError: (error) => {
      if (error instanceof ApiError && error.fieldErrors) {
        setErrors(error.fieldErrors)
        setFocusTick((tick) => tick + 1)
      } else {
        toast.show(error instanceof ApiError ? error.detail : '儲存失敗，請稍後再試', 'danger')
      }
    },
  })

  const resubmit = useMutation({
    mutationFn: () => api.post<OrganizationView>('/api/organizations/me/resubmit'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organization'] }),
  })

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const next: Record<string, string> = {}
    const need = (key: keyof typeof form, message: string) => {
      if (!String(form[key]).trim()) next[key] = message
    }
    need('name', '請填寫機構名稱')
    need('contactPerson', '請填寫承辦人姓名')
    need('responsiblePerson', '請填寫負責人姓名')
    need('withholdingTaxId', '請填寫扣繳單位統一編號')
    if (form.withholdingTaxId.trim() && !isValidTaxId(form.withholdingTaxId)) next.withholdingTaxId = '請填寫 8 碼數字的統一編號'
    need('contactEmail', '請填寫聯絡信箱')
    if (form.contactEmail.trim() && !/^\S+@\S+\.\S+$/.test(form.contactEmail.trim())) {
      next.contactEmail = '聯絡信箱格式不正確'
    }
    if (!form.mobilePhone.trim()) next.mobilePhone = '請填寫行動電話'
    else if (!isCompleteMobile(form.mobilePhone)) next.mobilePhone = '行動電話請填 10 碼，例如 0912-345-678'
    if (form.contactPhone.trim() && !isCompleteLandline(form.contactPhone)) {
      next.contactPhone = '聯絡電話格式不正確，例如 04-2345-6789'
    }
    need('description', '請填寫機構簡介')
    if (form.website.trim() && !/^https?:\/\/\S+\.\S+/.test(form.website.trim())) {
      next.website = '請填寫完整網址，例如 https://example.org'
    }
    if (!photo && !organization.photoUrl) next.photo = '請至少上傳一個檔案'
    need('address', '請填寫收件地址')
    need('preferredDeliveryTime', '請填寫方便收件時間')
    setErrors(next)
    if (Object.keys(next).length) {
      setFocusTick((tick) => tick + 1)
      return
    }
    save.mutate()
  }

  const introLength = [...form.description].length
  const previewUrl = photo?.url ?? organization.photoUrl
  const err = (key: string) => errors[key]

  return (
    <section className="org-tab" aria-labelledby="orgSetTitle">
      <div className="org-head"><h1 id="orgSetTitle">機構設定</h1></div>

      <form id="orgSettings" ref={formRef} noValidate onSubmit={submit}>
        <section className="panel set-sec" aria-labelledby="setInfoTitle">
          <h2 className="set-sec__title" id="setInfoTitle">機構資料</h2>
          <div className="form-grid">
            <FormField label="機構名稱" required span={6} error={err('name')}>
              <Input maxLength={120} value={form.name} onChange={update('name')} />
            </FormField>
            <FormField label="承辦人姓名" required span={3} error={err('contactPerson')}
              hint="平台審核與聯繫時的窗口，不會顯示給認領者">
              <Input maxLength={100} autoComplete="name" value={form.contactPerson}
                onChange={update('contactPerson')} />
            </FormField>
            <FormField label="聯絡信箱" required span={3} error={err('contactEmail')}
              hint="不會顯示給認領者">
              <Input type="email" maxLength={255} autoComplete="email" value={form.contactEmail}
                onChange={update('contactEmail')} />
            </FormField>
            <FormField label="負責人姓名" required span={3} error={err('responsiblePerson')}
              hint="機構的負責人、代表人或管理人">
              <Input maxLength={100} autoComplete="off" value={form.responsiblePerson}
                onChange={update('responsiblePerson')} />
            </FormField>
            <FormField label="扣繳單位統一編號" required span={3} error={err('withholdingTaxId')}
              hint="8 碼數字">
              <Input inputMode="numeric" maxLength={8} autoComplete="off" value={form.withholdingTaxId}
                onChange={(event) => {
                  setForm((current) => ({ ...current, withholdingTaxId: digitsOnly(event.target.value) }))
                  clearError('withholdingTaxId')
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
            <FormField label="機構簡介" required span={6} error={err('description')}
              hint="100 字以內，會顯示在願望詳細頁">
              <TextArea rows={3} maxLength={INTRO_MAX} aria-describedby="sIntroCount"
                value={form.description} onChange={update('description')} />
              <span id="sIntroCount" className={`char-count${introLength >= INTRO_MAX ? ' is-full' : ''}`}
                aria-live="polite">{introLength} / {INTRO_MAX}</span>
            </FormField>
            <FormField label="機構官方網站" span={6} error={err('website')}>
              <Input type="url" maxLength={255} placeholder="https://"
                value={form.website} onChange={update('website')} />
            </FormField>
            <FormField as="div" label="機構照片" required span={6} error={err('photo')}
              hint={<>選好照片後可以拖曳、縮放，裁成橫式 3:2（輸出 1200 × 800）；只會保存裁切後的照片。請避免<span className="nobr">拍到孩子的臉。</span></>}>
              <div className="photo-pick">
                <div className={`photo-preview${previewUrl ? ' has-img' : ''}`} aria-hidden={previewUrl ? undefined : true}>
                  <PreviewImage src={previewUrl} alt="機構照片" />
                  <span>3:2 橫式</span>
                  <i className="photo-preview__safe" />
                </div>
                <div className="upload">
                  <label className="upload__btn">
                    <ImagePlus className="icon" aria-hidden />
                    更換照片
                    <input type="file" accept={ACCEPTED_ORG_PHOTO_TYPES.join(',')} aria-label="機構照片"
                      onChange={(event) => {
                        const file = event.target.files?.[0]
                        event.target.value = ''
                        if (file) choosePhoto(file)
                      }} />
                  </label>
                  {photo && (
                    <span className="upload__list">
                      <span className="chip">
                        <span>{photo.file.name}</span>
                        <button type="button" aria-label="取消更換照片" onClick={() => {
                          if (photoUrlRef.current) URL.revokeObjectURL(photoUrlRef.current)
                          photoUrlRef.current = null
                          setPhoto(null)
                        }}><X className="icon" aria-hidden /></button>
                      </span>
                    </span>
                  )}
                </div>
              </div>
              {photo?.warn && <span className="photo-warn">{photo.warn}</span>}
            </FormField>
            <DocumentsField organizationId={organization.id} />
          </div>
        </section>

        <section className="panel set-sec" aria-labelledby="setShipTitle">
          <h2 className="set-sec__title" id="setShipTitle">收件資訊</h2>
          <p className="set-sec__desc">會顯示給認領了貴機構願望的認領者，幫助禮物順利送達。</p>
          <div className="form-grid">
            <FormField label="收件地址" required span={6} error={err('address')}>
              <Input maxLength={255} autoComplete="street-address" value={form.address}
                onChange={update('address')} />
            </FormField>
            <FormField label="方便收件時間" required span={6} error={err('preferredDeliveryTime')}
              hint="例如「平日上午 9:00–下午 5:00」">
              <Input maxLength={255} value={form.preferredDeliveryTime}
                onChange={update('preferredDeliveryTime')} />
            </FormField>
          </div>
        </section>

        <section className="panel set-sec" aria-labelledby="setOverdueTitle">
          <h2 className="set-sec__title" id="setOverdueTitle">逾期未寄送的處理</h2>
          <p className="set-sec__desc">
            認領後遲遲未寄送會讓孩子的願望一直卡著。你可以選擇讓系統自動收回，或只收到提醒後自行決定。
            設定變更只影響之後的新認領，不會動到既有的認領。
          </p>
          <div className="choices" role="radiogroup" aria-labelledby="setOverdueTitle">
            <PolicyOption value="MANUAL" current={form.releasePolicy} title="我自己決定"
              description="超過寄出期限時，系統在該願望傳一則新訊息提醒你，由你聯繫認領者後決定是否收回。"
              onSelect={(policy) => setForm((current) => ({ ...current, releasePolicy: policy }))} />
            <PolicyOption value="AUTO" current={form.releasePolicy} title="自動收回"
              description="超過寄出期限加上寬限天數，仍未回報寄送，系統自動收回並讓願望重新上架。"
              onSelect={(policy) => { graceAnimate.current = policy === 'AUTO'; setForm((current) => ({ ...current, releasePolicy: policy })) }} />
          </div>
          {form.releasePolicy === 'AUTO' && (
            <div className="grace" ref={(node) => {
              if (node && graceAnimate.current && !reducedMotion()) {
                graceAnimate.current = false
                node.animate([{ opacity: 0, transform: 'translateY(-6px)' }, { opacity: 1, transform: 'none' }],
                  { duration: 260, easing: EASE_OUT })
              }
            }}>
              <label className="field__label" htmlFor="sGrace">寬限天數</label>
              <SelectBox id="sGrace" className="grace__select" value={form.releaseAfterDays}
                onChange={(event) => setForm((current) => ({
                  ...current, releaseAfterDays: Number(event.target.value),
                }))}>
                {GRACE_DAYS.map((days) => <option key={days} value={days}>超過期限 {days} 天</option>)}
              </SelectBox>
            </div>
          )}
        </section>

        {organization.status === 'SUSPENDED' && <UnlockSuspensionSection />}

        {organization.status === 'REJECTED' && (
          <section className="panel set-sec" aria-labelledby="setResubmitTitle">
            <h2 className="set-sec__title" id="setResubmitTitle">重新送審</h2>
            <p className="set-sec__desc">資料補齊後可以再次送審，平台會重新檢視。</p>
            {resubmit.isError && <ErrorNotice error={resubmit.error} />}
            <div>
              <button type="button" className="btn" disabled={resubmit.isPending}
                onClick={() => resubmit.mutate()}>
                {resubmit.isPending ? '送出中…' : '重新送審'}
              </button>
            </div>
          </section>
        )}

        <div className="set-actions">
          <button type="submit" className="btn btn--primary" disabled={save.isPending}>
            {save.isPending ? '儲存中…' : '儲存變更'}
          </button>
        </div>
      </form>
      <ImageCropDialog file={cropping} aspect={3 / 2} outputWidth={1200} safeArea title="裁切機構照片"
        hint="拖曳照片移動位置，用滑桿或滾輪放大縮小；白色虛線框是 3:2 的完整範圍，綠色虛線之間是手機與側欄一定看得到的部分，重要的人事物請放在這裡面。"
        onCancel={() => setCropping(null)}
        onConfirm={(cropped) => { applyPhoto(cropped); setCropping(null) }} />
    </section>
  )
}

/**
 * 停權期間：資料修改好、證明文件補上之後，申請解除停權。同時只能有一筆待審，可以撤回；
 * 被退回會看到原因，可以修改後再申請。管理員核准就是復權。
 */
function UnlockSuspensionSection() {
  const queryClient = useQueryClient()
  const [message, setMessage] = useState('')
  const request = useQuery({
    queryKey: ['org-unlock-request'],
    queryFn: () => api.get<UnlockRequestView | null>('/api/organizations/me/unlock-request'),
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['org-unlock-request'] })
  const submit = useMutation({
    mutationFn: () => api.post<UnlockRequestView>('/api/organizations/me/unlock-request', { message: message.trim() || undefined }),
    onSuccess: () => { setMessage(''); void refresh() },
  })
  const withdraw = useMutation({
    mutationFn: () => api.post<UnlockRequestView>('/api/organizations/me/unlock-request/withdraw'),
    onSuccess: () => void refresh(),
  })
  const latest = request.data
  const pending = latest?.status === 'PENDING'

  return (
    <section className="panel set-sec" aria-labelledby="setUnlockTitle">
      <h2 className="set-sec__title" id="setUnlockTitle">申請解除停權</h2>
      <p className="set-sec__desc">
        請先在上面修改機構資料、在「相關文件證明」補上傳文件，並在「與團隊對話」說明處理情況，再送出申請。
        管理員審核通過後，機構與願望會恢復。
      </p>
      {pending ? (
        <p className="set-sec__desc">
          已送出申請，等管理員審核。
          <button type="button" className="link-btn" disabled={withdraw.isPending}
            onClick={() => withdraw.mutate()}>撤回申請</button>
        </p>
      ) : (
        <>
          {latest?.status === 'REJECTED' && (
            <p className="set-sec__desc">上一次的申請被退回：{latest.decisionNote ?? '—'}。可以處理後再次申請。</p>
          )}
          <div className="field">
            <label className="field__label" htmlFor="unsMessage">給管理員的說明（選填）</label>
            <textarea id="unsMessage" className="input" rows={3} maxLength={1000} value={message}
              onChange={(event) => setMessage(event.target.value)} />
          </div>
          {submit.isError && <ErrorNotice error={submit.error} />}
          <div>
            <button type="button" className="btn btn--primary" disabled={submit.isPending}
              onClick={() => submit.mutate()}>{submit.isPending ? '送出中…' : '送出申請'}</button>
          </div>
        </>
      )}
      {withdraw.isError && <ErrorNotice error={withdraw.error} />}
    </section>
  )
}

/**
 * 申請時上傳的立案證明文件。設計稿的機構設定沒有這一塊，但後台仍要讓機構看到自己送審的文件，
 * 所以保留在機構資料底下。機構不能自行刪除（需聯繫管理員）。
 */
function DocumentsField({ organizationId }: { organizationId: string }) {
  const queryClient = useQueryClient()
  const documents = useQuery({
    queryKey: ['organization', 'me', 'documents'],
    queryFn: () => api.get<AttachmentView[]>('/api/organizations/me/documents'),
  })
  const [problem, setProblem] = useState<string | null>(null)
  const count = documents.data?.length ?? 0
  const atLimit = count >= MAX_ORG_DOCUMENTS

  async function add(file: File) {
    const bad = validateOrgDocument(file)
    if (bad) { setProblem(bad); return }
    setProblem(null)
    try {
      await uploadImage('ORG_DOCUMENT', organizationId, file)
      await queryClient.invalidateQueries({ queryKey: ['organization', 'me', 'documents'] })
    } catch (error) {
      setProblem(error instanceof ApiError ? error.detail : '上傳失敗，請稍後再試')
    }
  }

  return (
    <FormField as="div" label="相關文件證明" span={6} error={problem}
      hint={`已上傳 ${count} / ${MAX_ORG_DOCUMENTS} 份；需要更換或刪除請聯繫平台管理員協助處理`}>
      {documents.isLoading && <Spinner label="載入文件" />}
      {documents.isError && <ErrorNotice error={documents.error} />}
      <div className="upload">
        {!atLimit && (
          <label className="upload__btn">
            <FilePlus className="icon" aria-hidden />
            新增文件
            <input type="file" aria-label="相關文件證明" accept={ACCEPTED_ORG_DOCUMENT_TYPES.join(',')}
              onChange={(event) => {
                const file = event.target.files?.[0]
                event.target.value = ''
                if (file) void add(file)
              }} />
          </label>
        )}
        <span className="upload__list">
          {documents.data && <AttachmentGallery files={documents.data} altPrefix="證明文件" />}
        </span>
      </div>
    </FormField>
  )
}

function PolicyOption({ value, current, title, description, onSelect }: {
  value: ReleasePolicy
  current: ReleasePolicy
  title: string
  description: string
  onSelect: (policy: ReleasePolicy) => void
}) {
  return (
    <label className="choice">
      <input type="radio" name="overdue" checked={current === value} onChange={() => onSelect(value)} />
      <span><strong>{title}</strong><span>{description}</span></span>
    </label>
  )
}
