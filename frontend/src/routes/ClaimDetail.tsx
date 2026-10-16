import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, api } from '../lib/api'
import { daysUntil, formatDate, formatPlainDate, todayPlainDate } from '../lib/format'
import {
  ACCEPTED_IMAGE_TYPES, ACCEPTED_RECEIPT_PROOF_TYPES, MAX_RECEIPT_PROOFS, MAX_SHIPPING_PROOFS,
  uploadImage, validateImage, validateReceiptProof,
} from '../lib/upload'
import type { AttachmentView, ClaimDonorView, ClaimEventType, ClaimEventView, ClaimStatus } from '../lib/types'
import { Breadcrumb } from '../components/Breadcrumb'
import { ErrorBanner, Notice, Spinner } from '../components/Feedback'
import { Button, Field, TextInput } from '../components/Form'
import { ImageUploader } from '../components/ImageUploader'
import { MessageThread } from '../components/MessageThread'
import { ClaimStatusBadge, OverdueBadge } from '../components/StatusBadge'
import { Timeline } from '../components/Timeline'

const CLOSED_STATUSES = ['COMPLETED', 'RELEASED', 'CANCELLED']

export function ClaimDetail() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()

  const claim = useQuery({
    queryKey: ['claim', id],
    queryFn: () => api.get<ClaimDonorView>(`/api/claims/${id}`),
  })

  const timeline = useQuery({
    queryKey: ['claim', id, 'timeline'],
    queryFn: () => api.get<ClaimEventView[]>(`/api/claims/${id}/timeline`),
  })

  const attachments = useQuery({
    queryKey: ['claim', id, 'attachments'],
    queryFn: () => api.get<AttachmentView[]>(`/api/claims/${id}/attachments`),
  })

  function refreshAll() {
    void queryClient.invalidateQueries({ queryKey: ['claim', id] })
    void queryClient.invalidateQueries({ queryKey: ['claim', id, 'attachments'] })
    void queryClient.invalidateQueries({ queryKey: ['claims'] })
  }

  if (claim.isLoading) return <Spinner label="載入認領" />
  if (claim.isError) {
    return <ErrorBanner error={claim.error} onRetry={() => void claim.refetch()} />
  }

  const data = claim.data!
  const closed = CLOSED_STATUSES.includes(data.status)
  const feedbackPhotos = attachments.data?.filter((a) => a.purpose === 'ORG_FEEDBACK') ?? []
  const shippingProofs = attachments.data?.filter((a) => a.purpose === 'SHIPPING_PROOF') ?? []

  return (
    <div className="space-y-8">
      <header>
        <Breadcrumb items={[
          { label: '我的認領', to: '/me/claims' },
          { label: data.wishTitle },
        ]} />
        <div className="mt-2 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-3xl font-bold text-white">{data.wishTitle}</h1>
            <p className="mt-1 text-slate-300">
              給 {data.childAlias}・{data.ageRangeLabel}・{data.organizationName}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {data.overdue && <OverdueBadge />}
            <ClaimStatusBadge status={data.status} />
          </div>
        </div>
      </header>

      {data.status === 'CLAIMED' && <ShipDeadlineNotice deadline={data.shipDeadlineAt} />}
      {data.releaseReason && (
        <Notice tone="warning">這筆認領已結束：{data.releaseReason}</Notice>
      )}

      <div className="grid gap-8 lg:grid-cols-[1.3fr_1fr]">
        <div className="space-y-8">
          {/* 放在「回報寄送」之前：要先知道寄去哪，才談得上回報寄了沒 */}
          <ShippingAddressPanel claim={data} />

          {data.status === 'CLAIMED' && (
            <Panel title="回報寄送">
              <ShipForm claimId={id} onDone={refreshAll} />
            </Panel>
          )}

          {/* 待寄送時物流業者/追蹤碼/照片三者在「回報寄送」表單一次填完；一旦寄出，
              這三者改用這個合併後的唯讀區塊顯示——跟待寄送那邊同一組資訊，不要
              拆成兩個區塊（寄送資訊／寄送證明）各顯示一半 */}
          {data.status !== 'CLAIMED' && (
            <Panel
              title="寄送資訊"
              action={!closed && shippingProofs.length < MAX_SHIPPING_PROOFS && (
                <ImageUploader
                  purpose="SHIPPING_PROOF"
                  targetId={id}
                  label="上傳照片"
                  onUploaded={() => {
                    void queryClient.invalidateQueries({ queryKey: ['claim', id, 'attachments'] })
                  }}
                />
              )}
            >
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <dt className="text-xs text-slate-400">物流業者</dt>
                  <dd className="mt-0.5 font-medium text-white">{data.trackingCarrier}</dd>
                </div>
                <div>
                  <dt className="text-xs text-slate-400">追蹤碼</dt>
                  <dd className="mt-0.5 font-medium text-white">{data.trackingNumber}</dd>
                </div>
                {data.giftDescription && (
                  <div className="col-span-2">
                    <dt className="text-xs text-slate-400">實際禮物內容</dt>
                    <dd className="mt-0.5 font-medium text-white">{data.giftDescription}</dd>
                  </div>
                )}
                {data.giftQuantity != null && (
                  <div>
                    <dt className="text-xs text-slate-400">數量</dt>
                    <dd className="mt-0.5 font-medium text-white">{data.giftQuantity} 份</dd>
                  </div>
                )}
                {data.giftPrice != null && (
                  <div>
                    <dt className="text-xs text-slate-400">禮物價格</dt>
                    <dd className="mt-0.5 font-medium text-white">
                      {data.giftPrice.toLocaleString('zh-TW')} 元
                    </dd>
                  </div>
                )}
                {data.shippedOn && (
                  <div>
                    <dt className="text-xs text-slate-400">寄出日期</dt>
                    <dd className="mt-0.5 font-medium text-white">{formatPlainDate(data.shippedOn)}</dd>
                  </div>
                )}
                {data.receiptRequested && (
                  <div>
                    <dt className="text-xs text-slate-400">捐贈收據</dt>
                    <dd className="mt-0.5 font-medium text-white">已申請</dd>
                  </div>
                )}
              </dl>

              <div className="mt-4 border-t border-white/10 pt-4">
                <PhotoGrid photos={shippingProofs} emptyHint="上傳寄件單或包裹照片，讓機構安心。"
                  onDeleted={() => {
                    void queryClient.invalidateQueries({ queryKey: ['claim', id, 'attachments'] })
                  }} />
              </div>
            </Panel>
          )}

          {feedbackPhotos.length > 0 && (
            <Panel title="機構的回饋">
              <p className="mb-3 text-sm text-slate-400">
                這些照片只有你和機構看得到，網址會在數分鐘後失效。
              </p>
              <PhotoGrid photos={feedbackPhotos} emptyHint="" />
            </Panel>
          )}

          <Panel title="與機構對話">
            <MessageThread claimId={id} closed={closed} />
          </Panel>

          {!closed && data.status === 'CLAIMED' && (
            <Panel title="不方便繼續了？">
              <CancelForm claimId={id} onDone={refreshAll} />
            </Panel>
          )}
        </div>

        <Panel title="進度">
          {timeline.isLoading ? <Spinner label="載入歷程" /> : (
            <Timeline events={timeline.data ?? []} pendingSteps={pendingStepsFor(data.status)} />
          )}
        </Panel>
      </div>
    </div>
  )
}

function Panel({ title, action, children }: {
  title: string; action?: React.ReactNode; children: React.ReactNode
}) {
  return (
    <section className="glass-card p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="font-semibold text-white">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

/** 認領的主流程。每一步對應一個歷程事件，順序就是實際會走的順序。 */
const CLAIM_FLOW: { status: ClaimStatus; event: ClaimEventType }[] = [
  { status: 'CLAIMED', event: 'CLAIMED' },
  { status: 'SHIPPED', event: 'SHIPPED' },
  { status: 'RECEIVED', event: 'RECEIVED' },
  { status: 'COMPLETED', event: 'COMPLETED' },
]

/**
 * 還沒走到的步驟。認領已經結束（收回、逾期自動收回、取消）時流程不可能再往下走，
 * 就不列——狀態不在主流程裡的一律回傳空陣列。
 */
function pendingStepsFor(status: ClaimStatus): ClaimEventType[] {
  const current = CLAIM_FLOW.findIndex((step) => step.status === status)
  if (current < 0) return []
  return CLAIM_FLOW.slice(current + 1).map((step) => step.event)
}

function ShipDeadlineNotice({ deadline }: { deadline?: string }) {
  const remaining = daysUntil(deadline)
  if (remaining === null) return null

  return remaining >= 0 ? (
    <Notice>
      請於 <strong>{formatDate(deadline)}</strong> 前寄出禮物（還有 {remaining} 天）。
      寄出後記得回來回報，孩子才知道禮物在路上了。
    </Notice>
  ) : (
    <Notice tone="warning">
      已逾期 {Math.abs(remaining)} 天。機構可能會收回這個願望讓其他人認領——
      如果還想繼續，請盡快寄出並回報，或在下方與機構聯繫。
    </Notice>
  )
}

/**
 * 寄送地址。
 *
 * 這是捐贈者唯一看得到機構地址的地方——願望牆與願望詳情只有機構名稱。認領之後才需要
 * 知道寄去哪，那道界線由後端的擁有者檢查守著，不要把地址加進公開的願望視圖。
 */
function ShippingAddressPanel({ claim }: { claim: ClaimDonorView }) {
  const hasAddress = Boolean(claim.organizationAddress)

  return (
    <Panel title="寄送地址">
      {hasAddress ? (
        <dl className="space-y-3 text-sm">
          <div>
            <dt className="text-xs text-slate-400">收件單位</dt>
            <dd className="mt-0.5 font-medium text-white">{claim.organizationName}</dd>
          </div>
          <div>
            <dt className="text-xs text-slate-400">地址</dt>
            {/* 這是要照著抄去寄件的資訊，給它最高的對比 */}
            <dd className="mt-0.5 text-base font-semibold text-white">
              {claim.organizationAddress}
            </dd>
          </div>
          {claim.organizationPreferredDeliveryTime && (
            <div>
              <dt className="text-xs text-slate-400">方便收件時間</dt>
              <dd className="mt-0.5 font-medium text-white">
                {claim.organizationPreferredDeliveryTime}
              </dd>
            </div>
          )}
          {claim.organizationPhone && (
            <div>
              <dt className="text-xs text-slate-400">聯絡電話</dt>
              <dd className="mt-0.5 font-medium text-white">{claim.organizationPhone}</dd>
            </div>
          )}
          {/* 兩支各自判斷而不是二選一：機構兩個都填了就兩個都給——市話找不到人時
              手機是唯一的補救，而這個面板的用途就是「把禮物寄到」需要的全部資訊 */}
          {claim.organizationMobilePhone && (
            <div>
              <dt className="text-xs text-slate-400">行動電話</dt>
              <dd className="mt-0.5 font-medium text-white">{claim.organizationMobilePhone}</dd>
            </div>
          )}
        </dl>
      ) : (
        // 地址在機構註冊時是必填，電話則是兩種至少填一個；但更早之前建立的機構
        // 可能連地址都沒有
        <Notice tone="warning">
          這個機構還沒有填寫收件地址，請用下方的訊息詢問機構。
        </Notice>
      )}
    </Panel>
  )
}

const RECEIPT_ID_PATTERN = /^([A-Za-z][A-Da-d0-9]\d{8}|\d{8})$/

/**
 * 回報寄送：物流業者／追蹤碼／禮物資訊／寄件單或包裹照片（以及選填的捐贈收據）一次送出。
 *
 * 照片故意不用 ImageUploader 的「選到就立刻上傳」行為——這裡要的是「按下
 * 我已經寄出了」那一刻，照片跟物流資訊同時送出、一起檢查必填，所以先在
 * 本地保留選到的 File，送出時才依序「上傳照片 → 呼叫 /ship」。
 *
 * 送出前有一道二次確認：回報寄送與捐贈收據資料送出後都無法更改，要讓人在按下去之前
 * 最後確認一次，尤其是需要收據的人。
 */
function ShipForm({ claimId, onDone }: { claimId: string; onDone: () => void }) {
  const [carrier, setCarrier] = useState('')
  const [trackingNumber, setTrackingNumber] = useState('')
  const [giftDescription, setGiftDescription] = useState('')
  // 數字欄位用字串存：輸入到一半（空字串）不該被硬轉成 0
  const [giftQuantity, setGiftQuantity] = useState('')
  const [giftPrice, setGiftPrice] = useState('')
  const [shippedOn, setShippedOn] = useState(todayPlainDate)
  const [photos, setPhotos] = useState<File[]>([])
  const [photoError, setPhotoError] = useState<string | null>(null)

  // 捐贈收據：預設關閉，開啟後下面這些全部必填
  const [receiptEnabled, setReceiptEnabled] = useState(false)
  const [receipt, setReceipt] = useState({ name: '', idNumber: '', email: '', address: '' })
  const [receiptProofs, setReceiptProofs] = useState<File[]>([])
  const [receiptProofError, setReceiptProofError] = useState<string | null>(null)
  const [idNumberError, setIdNumberError] = useState<string | null>(null)

  const [confirming, setConfirming] = useState(false)
  // 已經上傳成功的檔案。送出失敗（例如後端驗證沒過）後使用者修正再送，
  // 不能把同一批檔案再傳一次——附件有張數上限，重傳會把名額吃光
  const uploaded = useRef(new Set<File>())

  const ship = useMutation({
    mutationFn: async () => {
      for (const photo of photos) {
        if (uploaded.current.has(photo)) continue
        await uploadImage('SHIPPING_PROOF', claimId, photo)
        uploaded.current.add(photo)
      }
      if (receiptEnabled) {
        for (const proof of receiptProofs) {
          if (uploaded.current.has(proof)) continue
          await uploadImage('RECEIPT_PROOF', claimId, proof)
          uploaded.current.add(proof)
        }
      }
      return api.post(`/api/claims/${claimId}/ship`, {
        carrier,
        trackingNumber,
        giftDescription,
        giftQuantity: Number(giftQuantity),
        giftPrice: Number(giftPrice),
        shippedOn,
        receipt: receiptEnabled
          ? { ...receipt, idNumber: receipt.idNumber.trim().toUpperCase() }
          : undefined,
      })
    },
    onSuccess: onDone,
    // 失敗就收起確認區塊，讓使用者看到錯誤並回頭修正
    onError: () => setConfirming(false),
  })

  const fieldErrors = ship.error instanceof ApiError ? ship.error.fieldErrors : undefined

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    let valid = true

    if (photos.length === 0) {
      setPhotoError('請至少上傳一張寄件單或包裹照片')
      valid = false
    }
    if (receiptEnabled) {
      if (!RECEIPT_ID_PATTERN.test(receipt.idNumber.trim())) {
        setIdNumberError('格式不正確：身分證字號為 1 個英文字母加 9 碼數字，統一編號為 8 碼數字')
        valid = false
      }
      if (receiptProofs.length === 0) {
        setReceiptProofError('請至少上傳一份紙本或電子發票證明')
        valid = false
      }
    }
    if (!valid) return

    setPhotoError(null)
    setReceiptProofError(null)
    setIdNumberError(null)
    ship.reset()
    setConfirming(true)
  }

  const updateReceipt = (key: keyof typeof receipt) => (
    event: React.ChangeEvent<HTMLInputElement>,
  ) => {
    setReceipt((current) => ({ ...current, [key]: event.target.value }))
    if (key === 'idNumber') setIdNumberError(null)
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit}>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="物流業者" required error={fieldErrors?.carrier}>
          <TextInput required maxLength={60} value={carrier}
            placeholder="黑貓宅急便、郵局…"
            onChange={(event) => setCarrier(event.target.value)} />
        </Field>
        <Field label="追蹤碼" required error={fieldErrors?.trackingNumber}>
          <TextInput required maxLength={80} value={trackingNumber}
            onChange={(event) => setTrackingNumber(event.target.value)} />
        </Field>
      </div>
      <div className="grid gap-4 sm:grid-cols-[1fr_9rem]">
        <Field label="實際禮物內容" required error={fieldErrors?.giftDescription}>
          <TextInput required maxLength={200} value={giftDescription}
            placeholder="例如：繪本三本與文具組"
            onChange={(event) => setGiftDescription(event.target.value)} />
        </Field>
        <Field label="數量" required error={fieldErrors?.giftQuantity}>
          <UnitInput unit="份" min={1} max={9999} value={giftQuantity}
            onChange={setGiftQuantity} />
        </Field>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="禮物價格" required inlineHint hint="以發票資訊為主"
          error={fieldErrors?.giftPrice}>
          <UnitInput unit="元" min={0} max={10000000} value={giftPrice}
            onChange={setGiftPrice} />
        </Field>
        <Field label="寄出日期" required error={fieldErrors?.shippedOn}>
          <TextInput required type="date" max={todayPlainDate()} value={shippedOn}
            onChange={(event) => setShippedOn(event.target.value)} />
        </Field>
      </div>

      <FilePicker
        label="上傳寄件單或包裹照片"
        buttonLabel="新增照片"
        files={photos}
        onChange={setPhotos}
        max={MAX_SHIPPING_PROOFS}
        accept={ACCEPTED_IMAGE_TYPES}
        validate={validateImage}
        error={photoError}
        onError={setPhotoError}
      />

      <div className="space-y-4 rounded-lg border border-white/10 bg-white/5 p-4">
        <label className="flex items-start gap-2.5 text-sm text-slate-200">
          <input type="checkbox" className="mt-0.5" checked={receiptEnabled}
            onChange={(event) => setReceiptEnabled(event.target.checked)} />
          <span>
            <span className="font-medium text-white">我需要捐贈收據</span>
            <span className="mt-0.5 block text-xs text-slate-400">
              填寫的資料只有平台管理員看得到，機構看不到。
            </span>
          </span>
        </label>

        {receiptEnabled && (
          <div className="space-y-4">
            <Field label="姓名或公司名稱" required hint="需與報稅身分一致"
              error={fieldErrors?.['receipt.name']}>
              <TextInput required maxLength={100} value={receipt.name}
                onChange={updateReceipt('name')} />
            </Field>
            <Field label="身分證字號或統一編號" required
              hint="個人填身分證字號，公司填統一編號"
              error={idNumberError ?? fieldErrors?.['receipt.idNumber']}>
              <TextInput required maxLength={10} autoComplete="off" value={receipt.idNumber}
                onChange={updateReceipt('idNumber')} />
            </Field>
            <Field label="電子信箱" required hint="電子收據會寄到這裡"
              error={fieldErrors?.['receipt.email']}>
              <TextInput required type="email" maxLength={255} value={receipt.email}
                onChange={updateReceipt('email')} />
            </Field>
            <Field label="收件地址" required
              hint="需要紙本收據時用。預設寄電子檔，改紙本請聯繫 onlinesanta2026@gmail.com"
              error={fieldErrors?.['receipt.address']}>
              <TextInput required maxLength={255} value={receipt.address}
                onChange={updateReceipt('address')} />
            </Field>
            <FilePicker
              label="上傳紙本或電子發票證明"
              buttonLabel="新增檔案"
              files={receiptProofs}
              onChange={setReceiptProofs}
              max={MAX_RECEIPT_PROOFS}
              accept={ACCEPTED_RECEIPT_PROOF_TYPES}
              validate={validateReceiptProof}
              error={receiptProofError}
              onError={setReceiptProofError}
            />
          </div>
        )}
      </div>

      {ship.isError && <ErrorBanner error={ship.error} />}

      {confirming ? (
        <div className="space-y-3 rounded-lg border border-amber-400/30 bg-amber-400/10 p-4">
          <p className="text-sm font-medium text-white">確定要送出回報寄送嗎？</p>
          <ul className="list-disc space-y-1 pl-5 text-sm text-slate-200">
            <li>送出後<strong>無法更改</strong>，請再確認物流與禮物資訊都正確。</li>
            {receiptEnabled ? (
              <li>
                你有申請捐贈收據，請確實填寫姓名、身分證字號或統一編號、電子信箱、
                收件地址並附上發票證明——這些資料送出後<strong>無法更改</strong>，
                填錯會影響收據開立，如需更正只能聯繫 onlinesanta2026@gmail.com。
              </li>
            ) : (
              <li>
                如果需要捐贈收據，請先按「返回修改」，勾選「我需要捐贈收據」並填寫相關資料；
                送出後就無法再補申請。
              </li>
            )}
          </ul>
          <div className="flex gap-2">
            <Button type="button" disabled={ship.isPending} onClick={() => ship.mutate()}>
              {ship.isPending ? '送出中…' : '確定送出'}
            </Button>
            <Button type="button" variant="ghost" disabled={ship.isPending}
              onClick={() => setConfirming(false)}>
              返回修改
            </Button>
          </div>
        </div>
      ) : (
        <Button type="submit">我已經寄出了</Button>
      )}
    </form>
  )
}

/**
 * 本地選檔的清單：選到的檔案先留在前端，等整張表單送出才一起上傳。
 *
 * 不用 Field：它是 <label>，點到標籤範圍內任何地方都會被轉給裡面隱藏的 file input，
 * 結果點同一列就開啟檔案選擇。這裡用 div，只有按鈕會觸發。
 */
function FilePicker({ label, buttonLabel, files, onChange, max, accept, validate, error, onError }: {
  label: string
  buttonLabel: string
  files: File[]
  onChange: (next: File[]) => void
  max: number
  accept: readonly string[]
  validate: (file: File) => string | null
  error: string | null
  onError: (message: string | null) => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)

  function handleFile(file: File) {
    if (files.length >= max) {
      onError(`最多上傳 ${max} 個檔案`)
      return
    }
    const problem = validate(file)
    if (problem) {
      onError(problem)
      return
    }
    onChange([...files, file])
    onError(null)
  }

  return (
    <div>
      <span className="field-label text-sm font-medium text-slate-700">
        {label}<span className="ml-0.5 text-berry-500">*</span>
      </span>
      <div className="mt-1.5">
        <input
          ref={inputRef}
          type="file"
          className="hidden"
          accept={accept.join(',')}
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) handleFile(file)
            event.target.value = ''
          }}
        />
        <div className="space-y-2">
          {files.map((file, index) => (
            <div key={`${file.name}-${index}`}
              className="flex items-center justify-between gap-2 rounded-lg border
                border-white/15 bg-white/5 px-3 py-2 text-sm">
              <span className="truncate text-slate-300">{file.name}</span>
              <button type="button" className="text-slate-400 hover:text-berry-400"
                onClick={() => onChange(files.filter((_, i) => i !== index))}>
                移除
              </button>
            </div>
          ))}
          {files.length < max && (
            <Button type="button" variant="secondary" onClick={() => inputRef.current?.click()}>
              {buttonLabel}
            </Button>
          )}
        </div>
      </div>
      {error && <span className="field-error mt-1 block text-xs text-berry-600">{error}</span>}
    </div>
  )
}

/**
 * 只能輸入整數的欄位，右側固定顯示單位。
 *
 * type="number" 仍然允許輸入 e、+、-、小數點，所以 onChange 再把非數字濾掉；
 * 瀏覽器原生的 min/max 驗證留在送出時把關。
 */
function UnitInput({ unit, min, max, value, onChange }: {
  unit: string; min: number; max: number
  value: string; onChange: (next: string) => void
}) {
  return (
    <div className="flex items-center gap-2">
      <TextInput required type="number" inputMode="numeric" step={1} min={min} max={max}
        value={value}
        onKeyDown={(event) => {
          if (['e', 'E', '+', '-', '.'].includes(event.key)) event.preventDefault()
        }}
        onChange={(event) => onChange(event.target.value.replace(/\D/g, ''))} />
      <span className="shrink-0 text-sm text-slate-300">{unit}</span>
    </div>
  )
}

function CancelForm({ claimId, onDone }: { claimId: string; onDone: () => void }) {
  const [confirming, setConfirming] = useState(false)
  const [reason, setReason] = useState('')

  const cancel = useMutation({
    mutationFn: () => api.post(`/api/claims/${claimId}/cancel`,
      reason.trim() ? { reason: reason.trim() } : undefined),
    onSuccess: onDone,
  })

  if (!confirming) {
    return (
      <div>
        <p className="mb-3 text-sm text-slate-300">
          取消後這個願望會立刻回到願望牆，讓其他人有機會認領。
        </p>
        <Button variant="secondary" onClick={() => setConfirming(true)}>取消認領</Button>
      </div>
    )
  }

  return (
    <form className="space-y-3" onSubmit={(event) => { event.preventDefault(); cancel.mutate() }}>
      <Field label="取消原因" hint="會記錄在歷程中，機構看得到">
        <TextInput maxLength={255} value={reason}
          onChange={(event) => setReason(event.target.value)} />
      </Field>
      {cancel.isError && <ErrorBanner error={cancel.error} />}
      <div className="flex gap-2">
        <Button variant="danger" type="submit" disabled={cancel.isPending}>
          {cancel.isPending ? '處理中…' : '確定取消'}
        </Button>
        <Button variant="ghost" onClick={() => setConfirming(false)}>先不要</Button>
      </div>
    </form>
  )
}

/**
 * 照片格線。`onDeleted` 只在「這組照片是目前使用者自己上傳的」時才傳——
 * 機構的回饋照片對捐贈者是唯讀的，不會有這個 prop。
 */
function PhotoGrid({ photos, emptyHint, onDeleted }: {
  photos: AttachmentView[]; emptyHint: string; onDeleted?: () => void
}) {
  if (photos.length === 0) {
    return emptyHint ? <p className="text-sm text-slate-400">{emptyHint}</p> : null
  }

  return (
    <div className="grid grid-cols-3 gap-3">
      {photos.map((photo) => (
        onDeleted
          ? <DeletablePhoto key={photo.id} photo={photo} onDeleted={onDeleted} />
          : <PhotoTile key={photo.id} photo={photo} />
      ))}
    </div>
  )
}

function PhotoTile({ photo }: { photo: AttachmentView }) {
  return (
    <a href={photo.url} target="_blank" rel="noreferrer"
      className="overflow-hidden rounded-xl ring-1 ring-white/15 transition-opacity
        hover:opacity-90">
      <img src={photo.url} alt="" className="aspect-square w-full object-cover" />
    </a>
  )
}

function DeletablePhoto({ photo, onDeleted }: { photo: AttachmentView; onDeleted: () => void }) {
  const [confirming, setConfirming] = useState(false)

  const remove = useMutation({
    mutationFn: () => api.delete(`/api/attachments/${photo.id}`),
    onSuccess: onDeleted,
  })

  return (
    <div className="group relative overflow-hidden rounded-xl ring-1 ring-white/15">
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
