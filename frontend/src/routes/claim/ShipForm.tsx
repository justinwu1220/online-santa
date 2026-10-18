import { useMutation } from '@tanstack/react-query'
import { Paperclip, X } from 'lucide-react'
import { useRef, useState } from 'react'
import { ApiError, api } from '../../lib/api'
import { todayPlainDate } from '../../lib/format'
import {
  ACCEPTED_IMAGE_TYPES, ACCEPTED_RECEIPT_PROOF_TYPES, MAX_RECEIPT_PROOFS, MAX_SHIPPING_PROOFS,
  uploadImage, validateImage, validateReceiptProof,
} from '../../lib/upload'
import { FormField, Input, UnitInput } from '../../components/ui/Fields'
import { ErrorNotice } from '../../components/ui/Spinner'

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
    <form onSubmit={handleSubmit} noValidate={false}>
      <div className="form-grid">
        <FormField label="物流業者" required span={3} error={fieldErrors?.carrier}>
          <Input required maxLength={60} value={carrier} list="carrier-list"
            placeholder="黑貓宅急便、郵局…"
            onChange={(event) => setCarrier(event.target.value)} />
          <datalist id="carrier-list">
            {CARRIERS.map((name) => <option key={name} value={name} />)}
          </datalist>
        </FormField>
        <FormField label="追蹤碼" required span={3} error={fieldErrors?.trackingNumber}>
          <Input required maxLength={80} value={trackingNumber}
            onChange={(event) => setTrackingNumber(event.target.value)} />
        </FormField>

        <FormField label="實際寄出的禮物" required span={4} hintBeside
          hint="名稱與規格，例如：蠟筆小新變形機器人（約 20 公分）" error={fieldErrors?.giftDescription}>
          <Input required maxLength={200} value={giftDescription}
            placeholder="例如：繪本三本與文具組"
            onChange={(event) => setGiftDescription(event.target.value)} />
        </FormField>
        <FormField label="數量" required span={2} hintBeside hint="以「份」計算" error={fieldErrors?.giftQuantity}>
          <UnitInput unit="份" min={1} max={9999} value={giftQuantity} onChange={setGiftQuantity} />
        </FormField>

        <FormField label="禮物價格" required span={3} hintBeside hint="以購買發票上的金額為準" error={fieldErrors?.giftPrice}>
          <UnitInput unit="元" min={0} max={10000000} value={giftPrice} onChange={setGiftPrice} />
        </FormField>
        <FormField label="寄出日期" required span={3} hintBeside hint="預設為今天" error={fieldErrors?.shippedOn}>
          <Input required type="date" max={todayPlainDate()} value={shippedOn}
            onChange={(event) => setShippedOn(event.target.value)} />
        </FormField>

        <FilePicker
          label="上傳寄件單或包裹照片"
          files={photos}
          onChange={setPhotos}
          max={MAX_SHIPPING_PROOFS}
          accept={ACCEPTED_IMAGE_TYPES}
          validate={validateImage}
          error={photoError}
          onError={setPhotoError}
        />

        <div className="c-6">
          <div className={`receipt${receiptEnabled ? ' is-open' : ''}`}>
            <label className="receipt__toggle">
              <input type="checkbox" checked={receiptEnabled}
                onChange={(event) => setReceiptEnabled(event.target.checked)} />
              <span>
                <span className="receipt__title">我需要捐贈收據</span>
                <span className="receipt__hint">填寫的資料只有平台管理員看得到，機構看不到。</span>
              </span>
            </label>
            <div className="receipt__more" inert={!receiptEnabled}>
              <div>
                <div className="receipt__fields">
                  <div className="form-grid">
                    <FormField label="姓名或公司名稱" required={receiptEnabled} span={3}
                      hintBeside hint="真實姓名或單位全銜，需與報稅身分一致" error={fieldErrors?.['receipt.name']}>
                      <Input required={receiptEnabled} maxLength={100} value={receipt.name}
                        onChange={updateReceipt('name')} />
                    </FormField>
                    <FormField label="身分證字號或統一編號" required={receiptEnabled} span={3}
                      hintBeside hint="個人填身分證或居留證號，公司填統一編號"
                      error={idNumberError ?? fieldErrors?.['receipt.idNumber']}>
                      <Input required={receiptEnabled} maxLength={10} autoComplete="off"
                        value={receipt.idNumber} onChange={updateReceipt('idNumber')} />
                    </FormField>
                    <FormField label="電子信箱" required={receiptEnabled} span={3}
                      hintBeside hint="電子收據會寄到這裡" error={fieldErrors?.['receipt.email']}>
                      <Input required={receiptEnabled} type="email" maxLength={255}
                        value={receipt.email} onChange={updateReceipt('email')} />
                    </FormField>
                    <FormField label="收件地址" required={receiptEnabled} span={3}
                      hintBeside hint="預設寄電子檔；要紙本請寫信到 onlinesanta2026@gmail.com"
                      error={fieldErrors?.['receipt.address']}>
                      <Input required={receiptEnabled} maxLength={255}
                        value={receipt.address} onChange={updateReceipt('address')} />
                    </FormField>
                    <FilePicker
                      label="上傳紙本或電子發票證明"
                      hint="供協會核對時價；請先遮住與這次捐贈無關的個人資料"
                      required={receiptEnabled}
                      files={receiptProofs}
                      onChange={setReceiptProofs}
                      max={MAX_RECEIPT_PROOFS}
                      accept={ACCEPTED_RECEIPT_PROOF_TYPES}
                      validate={validateReceiptProof}
                      error={receiptProofError}
                      onError={setReceiptProofError}
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {ship.isError && <div style={{ marginTop: 16 }}><ErrorNotice error={ship.error} /></div>}

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
function FilePicker({ label, hint, required = true, files, onChange, max, accept, validate, error, onError }: {
  label: string
  hint?: string
  required?: boolean
  files: File[]
  onChange: (next: File[]) => void
  max: number
  accept: readonly string[]
  validate: (file: File) => string | null
  error: string | null
  onError: (message: string | null) => void
}) {
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
    <FormField as="div" label={label} required={required} span={6} hintBeside hint={hint} error={error}>
      <div className="upload">
        {files.map((file, index) => (
          <span key={`${file.name}-${index}`} className="chip">
            <span>{file.name}</span>
            <button type="button" aria-label={`移除 ${file.name}`}
              onClick={() => onChange(files.filter((_, i) => i !== index))}>
              <X size={14} aria-hidden />
            </button>
          </span>
        ))}
        {files.length < max && (
          <span className="upload__btn">
            <Paperclip className="icon" aria-hidden />
            {files.length === 0 ? '選擇檔案' : '再新增一個'}
            <input
              type="file"
              accept={accept.join(',')}
              aria-label={label}
              onChange={(event) => {
                const file = event.target.files?.[0]
                if (file) handleFile(file)
                event.target.value = ''
              }}
            />
          </span>
        )}
      </div>
    </FormField>
  )
}
