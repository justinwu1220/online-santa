import { useMutation } from '@tanstack/react-query'
import { FileUp, ImagePlus, Lock, X } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { ApiError, api } from '../../lib/api'
import { todayPlainDate } from '../../lib/format'
import { reducedMotion } from '../../lib/motion'
import {
  ACCEPTED_IMAGE_TYPES, ACCEPTED_RECEIPT_PROOF_TYPES, MAX_RECEIPT_PROOFS, MAX_SHIPPING_PROOFS,
  uploadImage, validateImage, validateReceiptProof,
} from '../../lib/upload'
import { FormField, Input, UnitInput } from '../../components/ui/Fields'
import { ErrorNotice } from '../../components/ui/Spinner'

/** 畫面上有對應欄位的後端欄位錯誤 key；其餘的錯誤沒有地方顯示，要用通用提示框。 */
const SHOWN_FIELDS = new Set([
  'carrier', 'trackingNumber', 'giftDescription', 'giftQuantity', 'giftPrice', 'shippedOn',
  'receipt.name', 'receipt.idNumber', 'receipt.email', 'receipt.address',
])

const RECEIPT_ID_PATTERN = /^([A-Za-z][A-Da-d0-9]\d{8}|\d{8})$/

/** 常用物流業者，輸入時當建議；不限於清單內的值。 */
const CARRIERS = ['黑貓宅急便', '新竹物流', '中華郵政', '統一速達', '全家便利配', '7-ELEVEN 交貨便']

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
export function ShipForm({ claimId, onDone }: { claimId: string; onDone: () => void }) {
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

  // 前端必填檢查的錯誤（key 是欄位名）。照原型：送出時一次檢查全部，錯誤顯示在欄位下方，
  // 並捲到第一個有問題的欄位、把焦點移進去
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [focusTick, setFocusTick] = useState(0)
  const form = useRef<HTMLFormElement>(null)

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
    // 失敗就收起確認區塊，讓使用者看到錯誤並回頭修正；欄位錯誤就捲到第一個有問題的欄位
    onError: (error) => {
      setConfirming(false)
      if (error instanceof ApiError && error.fieldErrors && Object.keys(error.fieldErrors).length > 0) {
        setFocusTick((n) => n + 1)
      }
    },
  })

  const fieldErrors = ship.error instanceof ApiError ? ship.error.fieldErrors : undefined
  const onlyFieldErrors = Boolean(fieldErrors)
    && Object.keys(fieldErrors!).length > 0
    && Object.keys(fieldErrors!).every((key) => SHOWN_FIELDS.has(key))

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    const found: Record<string, string> = {}
    const needText = (key: string, value: string, name: string) => {
      if (!value.trim()) found[key] = `請填寫${name}`
    }
    const needNumber = (key: string, value: string, name: string, min: number) => {
      if (!value.trim()) found[key] = `請填寫${name}`
      else if (Number(value) < min) found[key] = '請填寫正確的數字'
    }

    needText('carrier', carrier, '物流業者')
    needText('trackingNumber', trackingNumber, '追蹤碼')
    needText('giftDescription', giftDescription, '實際寄出的禮物')
    needNumber('giftQuantity', giftQuantity, '數量', 1)
    needNumber('giftPrice', giftPrice, '禮物價格', 0)
    needText('shippedOn', shippedOn, '寄出日期')
    if (photos.length === 0) found.photos = '請至少上傳一個檔案'
    if (receiptEnabled) {
      needText('receipt.name', receipt.name, '收據抬頭')
      if (!receipt.idNumber.trim()) found['receipt.idNumber'] = '請填寫身分證字號或統一編號'
      else if (!RECEIPT_ID_PATTERN.test(receipt.idNumber.trim())) {
        found['receipt.idNumber'] = '格式不正確：身分證字號為 1 個英文字母加 9 碼數字，統一編號為 8 碼數字'
      }
      if (!receipt.email.trim()) found['receipt.email'] = '請填寫收據寄送信箱'
      else if (!/^\S+@\S+\.\S+$/.test(receipt.email.trim())) found['receipt.email'] = '請確認信箱格式'
      needText('receipt.address', receipt.address, '收據寄送地址')
      if (receiptProofs.length === 0) found.receiptProofs = '請至少上傳一個檔案'
    }

    setErrors(found)
    setPhotoError(null)
    setReceiptProofError(null)
    setIdNumberError(null)
    if (Object.keys(found).length > 0) {
      setFocusTick((n) => n + 1)
      return
    }
    ship.reset()
    setConfirming(true)
  }

  // 錯誤顯示出來之後，捲到第一個有問題的欄位並聚焦（原型 validateShip）
  useEffect(() => {
    if (focusTick === 0) return
    const first = form.current?.querySelector<HTMLElement>('.field.is-invalid')
    if (!first) return
    first.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'center' })
    const target = first.querySelector<HTMLElement>('input:not([type=file])')
    const timer = window.setTimeout(() => target?.focus({ preventScroll: true }), reducedMotion() ? 0 : 300)
    return () => window.clearTimeout(timer)
  }, [focusTick])

  /** 補上檔案後，那一欄的「請至少上傳」提示就該消失（原型 files change 的行為） */
  const clearError = (key: string) => setErrors((current) => {
    if (!(key in current)) return current
    const { [key]: _removed, ...rest } = current
    return rest
  })

  const updateReceipt = (key: keyof typeof receipt) => (
    event: React.ChangeEvent<HTMLInputElement>,
  ) => {
    setReceipt((current) => ({ ...current, [key]: event.target.value }))
    clearError(`receipt.${key}`)
    if (key === 'idNumber') setIdNumberError(null)
  }

  return (
    <form ref={form} onSubmit={handleSubmit} noValidate>
      <div className="form-grid">
        <FormField label="物流業者" required span={3} error={errors.carrier ?? fieldErrors?.carrier}>
          <Input required maxLength={60} value={carrier} list="carrier-list"
            placeholder="黑貓宅急便、郵局…"
            onChange={(event) => { setCarrier(event.target.value); clearError('carrier') }} />
          <datalist id="carrier-list">
            {CARRIERS.map((name) => <option key={name} value={name} />)}
          </datalist>
        </FormField>
        <FormField label="追蹤碼" required span={3} error={errors.trackingNumber ?? fieldErrors?.trackingNumber}>
          <Input required maxLength={80} value={trackingNumber}
            onChange={(event) => { setTrackingNumber(event.target.value); clearError('trackingNumber') }} />
        </FormField>

        <FormField label="實際寄出的禮物" required span={4}
          hint="名稱與規格" error={errors.giftDescription ?? fieldErrors?.giftDescription}>
          <Input required maxLength={200} value={giftDescription}
            placeholder="蠟筆小新變形機器人（約 20 公分）"
            onChange={(event) => { setGiftDescription(event.target.value); clearError('giftDescription') }} />
        </FormField>
        <FormField label="數量" required span={2} hint="以「份」計算" error={errors.giftQuantity ?? fieldErrors?.giftQuantity}>
          <UnitInput unit="份" min={1} max={9999} value={giftQuantity} onChange={(next) => { setGiftQuantity(next); clearError('giftQuantity') }} />
        </FormField>

        <FormField label="禮物價格" required span={3} hint="以購買發票上的金額為準" error={errors.giftPrice ?? fieldErrors?.giftPrice}>
          <UnitInput unit="元" min={0} max={10000000} value={giftPrice} onChange={(next) => { setGiftPrice(next); clearError('giftPrice') }} />
        </FormField>
        <FormField label="寄出日期" required span={3} hint="預設為今天" error={errors.shippedOn ?? fieldErrors?.shippedOn}>
          <Input required type="date" max={todayPlainDate()} value={shippedOn}
            onChange={(event) => { setShippedOn(event.target.value); clearError('shippedOn') }} />
        </FormField>

        <FilePicker
          label="寄件單或包裹照片"
          buttonText="新增照片"
          icon={<ImagePlus className="icon" aria-hidden />}
          files={photos}
          onChange={(next) => { setPhotos(next); clearError('photos') }}
          max={MAX_SHIPPING_PROOFS}
          accept={ACCEPTED_IMAGE_TYPES}
          validate={validateImage}
          error={errors.photos ?? photoError}
          onError={setPhotoError}
        />

        <div className="c-6">
          <div className={`receipt${receiptEnabled ? ' is-open' : ''}`}>
            <label className="receipt__toggle">
              <input type="checkbox" checked={receiptEnabled}
                onChange={(event) => setReceiptEnabled(event.target.checked)} />
              <span>
                <span className="receipt__title">我需要捐贈收據</span>
                <span className="receipt__hint">
                  <Lock className="icon" aria-hidden />
                  <span>
                    勾選後填寫的資料只有平台管理員與協會開據人員看得到，機構看不到。
                    沒勾選時，協會仍會依你註冊的捐贈人姓名（或「善心人士」）開立收據。
                  </span>
                </span>
              </span>
            </label>
            <div className="receipt__more" inert={!receiptEnabled}>
              <div>
                <div className="receipt__fields">
                  <div className="form-grid">
                    <FormField label="收據抬頭" required={receiptEnabled} span={3}
                      hint="真實姓名或單位全銜，需與報稅身分一致" error={errors['receipt.name'] ?? fieldErrors?.['receipt.name']}>
                      <Input required={receiptEnabled} maxLength={100} value={receipt.name}
                        onChange={updateReceipt('name')} />
                    </FormField>
                    <FormField label="身分證字號或統一編號" required={receiptEnabled} span={3}
                      hint="個人填身分證或居留證號，公司填統一編號"
                      error={errors['receipt.idNumber'] ?? idNumberError ?? fieldErrors?.['receipt.idNumber']}>
                      <Input required={receiptEnabled} maxLength={10} autoComplete="off"
                        value={receipt.idNumber} onChange={updateReceipt('idNumber')} />
                    </FormField>
                    <FormField label="收據寄送信箱" required={receiptEnabled} span={3}
                      hint="電子收據會寄到這裡" error={errors['receipt.email'] ?? fieldErrors?.['receipt.email']}>
                      <Input required={receiptEnabled} type="email" maxLength={255}
                        value={receipt.email} onChange={updateReceipt('email')} />
                    </FormField>
                    <FormField label="收據寄送地址" required={receiptEnabled} span={3}
                      hint="預設寄電子檔；要紙本請寫信到 onlinesanta2026@gmail.com"
                      error={errors['receipt.address'] ?? fieldErrors?.['receipt.address']}>
                      <Input required={receiptEnabled} maxLength={255}
                        value={receipt.address} onChange={updateReceipt('address')} />
                    </FormField>
                    <FilePicker
                      label="紙本或電子發票"
                      buttonText="新增檔案"
                      icon={<FileUp className="icon" aria-hidden />}
                      hint="供協會核對時價；請先遮住與這次捐贈無關的個人資料"
                      required={receiptEnabled}
                      files={receiptProofs}
                      onChange={(next) => { setReceiptProofs(next); clearError('receiptProofs') }}
                      max={MAX_RECEIPT_PROOFS}
                      accept={ACCEPTED_RECEIPT_PROOF_TYPES}
                      validate={validateReceiptProof}
                      error={errors.receiptProofs ?? receiptProofError}
                      onError={setReceiptProofError}
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 欄位錯誤顯示在欄位下方並捲過去；這裡只放對應不到欄位的錯誤（409、網路錯誤…） */}
      {ship.isError && !onlyFieldErrors && (
        <div style={{ marginTop: 16 }}><ErrorNotice error={ship.error} /></div>
      )}

      {confirming ? (
        <div className="notice notice--warn" style={{ marginTop: 24 }}>
          <p><strong>確定要送出回報寄送嗎？</strong></p>
          <ul style={{ margin: '8px 0 0', paddingLeft: 20 }}>
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
          <div className="form-actions">
            <button type="button" className="btn btn--primary" disabled={ship.isPending}
              onClick={() => ship.mutate()}>
              {ship.isPending ? '送出中…' : '確定送出'}
            </button>
            <button type="button" className="btn btn--ghost" disabled={ship.isPending}
              onClick={() => setConfirming(false)}>
              返回修改
            </button>
          </div>
        </div>
      ) : (
        <div className="form-actions">
          <button type="submit" className="btn btn--primary">我已經寄出了</button>
        </div>
      )}
    </form>
  )
}

/**
 * 本地選檔的清單：選到的檔案先留在前端，等整張表單送出才一起上傳。
 *
 * 外框用 div 而不是 label：label 會把範圍內任何點擊轉給裡面的 file input，
 * 結果點同一列就開啟檔案選擇，只有按鈕該觸發。
 */
function FilePicker({ label, hint, buttonText, icon, required = true, files, onChange, max, accept, validate, error, onError }: {
  label: string
  hint?: string
  buttonText: string
  icon: ReactNode
  required?: boolean
  files: File[]
  onChange: (next: File[]) => void
  max: number
  accept: readonly string[]
  validate: (file: File) => string | null
  error: string | null
  onError: (message: string | null) => void
}) {
  /** 一次可以選多個：逐一驗證，遇到第一個不合格或超過上限就停下並顯示原因，前面合格的照樣加入。 */
  function handleFiles(picked: File[]) {
    const next = [...files]
    let problem: string | null = null
    for (const file of picked) {
      if (next.length >= max) {
        problem = `最多上傳 ${max} 個檔案`
        break
      }
      problem = validate(file)
      if (problem) break
      next.push(file)
    }
    if (next.length !== files.length) onChange(next)
    onError(problem)
  }

  return (
    <FormField as="div" label={label} required={required} span={6} hint={hint} error={error}>
      <div className="upload">
        {files.length < max && (
          <span className="upload__btn">
            {icon}
            {buttonText}
            <input
              type="file"
              multiple
              accept={accept.join(',')}
              aria-label={label}
              onChange={(event) => {
                handleFiles([...(event.target.files ?? [])])
                event.target.value = ''
              }}
            />
          </span>
        )}
        {files.map((file, index) => (
          <span key={`${file.name}-${index}`} className="chip">
            <span>{file.name}</span>
            <button type="button" aria-label={`移除 ${file.name}`}
              onClick={() => onChange(files.filter((_, i) => i !== index))}>
              <X size={14} aria-hidden />
            </button>
          </span>
        ))}
      </div>
    </FormField>
  )
}
