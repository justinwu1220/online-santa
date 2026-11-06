import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Dialog } from '../../components/ui/Dialog'
import { ImageCropDialog } from '../../components/ui/ImageCropDialog'
import { api, ApiError } from '../../lib/api'
import { uploadImage, validateImage } from '../../lib/upload'
import type { UnlockRequestView, WishFilterOptions, WishOrgView, WishRevisionBody } from '../../lib/types'

/**
 * 申請解除凍結：說明並（內容疑慮時）附上修改後的內容。禮物（願望標題）不能改——認領者可能已經買好了。
 * 送出後線上內容不變，管理員核准才會套用；審核期間認領者看到的仍是管理員處理過的版本。
 * 也可以附上新的信件照片（先裁成 4:3）：核准前不上牆，核准後取代原本的信件。
 */
export function UnlockRequestDialog({ open, wish, canRevise, onClose, onDone }: {
  open: boolean
  wish: WishOrgView
  /** 凍結原因是內容疑慮：可以附上修改後的內容 */
  canRevise: boolean
  onClose: () => void
  onDone: (request: UnlockRequestView) => void | Promise<void>
}) {
  const [message, setMessage] = useState('')
  const [alias, setAlias] = useState(wish.childAlias ?? '')
  const [ageRange, setAgeRange] = useState(wish.ageRange)
  const [description, setDescription] = useState(wish.description ?? '')
  const [error, setError] = useState('')
  // 新的信件照片：選檔 → 裁切 → 暫存在這裡，送出申請時才上傳（原圖不上傳）
  const [cropping, setCropping] = useState<File | null>(null)
  const [letter, setLetter] = useState<File | null>(null)
  const [letterUrl, setLetterUrl] = useState<string>()
  useEffect(() => {
    if (!letter) { setLetterUrl(undefined); return }
    const url = URL.createObjectURL(letter)
    setLetterUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [letter])

  const options = useQuery({
    queryKey: ['wish-options'],
    queryFn: () => api.get<WishFilterOptions>('/api/wishes/options'),
    enabled: open && canRevise,
  })

  // 每次開啟都以目前內容為起點（render 期間依 props 調整，不用 effect）
  const [lastOpen, setLastOpen] = useState(false)
  if (open !== lastOpen) {
    setLastOpen(open)
    if (open) {
      setMessage(''); setAlias(wish.childAlias ?? ''); setAgeRange(wish.ageRange)
      setDescription(wish.description ?? ''); setError(''); setLetter(null); setCropping(null)
    }
  }

  const submit = useMutation({
    mutationFn: async () => {
      const revision: WishRevisionBody = {}
      if (canRevise) {
        if (alias.trim() !== (wish.childAlias ?? '')) revision.childAlias = alias.trim()
        if (ageRange !== wish.ageRange) revision.ageRange = ageRange
        if (description.trim() !== (wish.description ?? '')) revision.description = description.trim()
        if (letter) revision.letterAttachmentId = (await uploadImage('WISH_LETTER_REVISION', wish.id, letter)).id
      }
      return api.post<UnlockRequestView>(`/api/organizations/me/wishes/${wish.id}/unlock-request`, {
        message: message.trim() || undefined,
        revision: Object.keys(revision).length > 0 ? revision : undefined,
      })
    },
    onSuccess: (request) => onDone(request),
    onError: (e) => setError(e instanceof ApiError ? e.detail : '送出失敗，請稍後再試'),
  })

  return (
    <Dialog open={open} onClose={onClose} labelledBy="unlockTitle" dismissible={!submit.isPending}>
      <h2 id="unlockTitle">申請解除凍結</h2>
      <p>
        {canRevise
          ? '請依管理員說明的原因修改下面可能含個資的欄位，再送出申請。管理員核准後，修改才會上線、凍結才會解除；禮物（願望標題）不能修改。'
          : '請說明處理情況後送出申請，管理員確認後會解除凍結。這次凍結不是內容疑慮，所以不能修改內容。'}
      </p>
      {canRevise && (
        <>
          <div className="field" style={{ marginTop: 12 }}>
            <label className="field__label" htmlFor="unAlias">孩子的暱稱</label>
            <input id="unAlias" className="input" maxLength={50} value={alias}
              onChange={(event) => setAlias(event.target.value)} />
          </div>
          <div className="field" style={{ marginTop: 12 }}>
            <label className="field__label" htmlFor="unAge">孩子的年齡</label>
            <select id="unAge" className="input" value={ageRange} onChange={(event) => setAgeRange(event.target.value)}>
              {options.data?.ageRanges.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              )) ?? <option value={wish.ageRange}>{wish.ageRangeLabel}</option>}
            </select>
          </div>
          <div className="field" style={{ marginTop: 12 }}>
            <label className="field__label" htmlFor="unDesc">願望說明（50 字內）</label>
            <textarea id="unDesc" className="input" rows={2} maxLength={50} value={description}
              onChange={(event) => setDescription(event.target.value)} />
          </div>
          <div className="field" style={{ marginTop: 12 }}>
            <span className="field__label" id="unLetterLabel">新的信件照片（選填）</span>
            <span className="field__hint">信件照片有個資時請換新的。可以裁切成 4:3；管理員核准後才會取代原本的信件，原圖不會上傳。</span>
            <div className="upload">
              <label className="upload__btn">
                {letter ? '重新選擇照片' : '選擇照片'}
                <input type="file" accept="image/*" aria-labelledby="unLetterLabel"
                  onChange={(event) => {
                    const file = event.target.files?.[0]
                    event.target.value = ''
                    if (!file) return
                    const problem = validateImage(file)
                    if (problem) setError(problem)
                    else { setError(''); setCropping(file) }
                  }} />
              </label>
              {letter && (
                <span className="chip">
                  <span>{letter.name}</span>
                  <button type="button" aria-label="移除這張照片" onClick={() => setLetter(null)}>×</button>
                </span>
              )}
            </div>
            {letterUrl && <img src={letterUrl} alt="新的信件預覽" style={{ marginTop: 8, maxWidth: 200, borderRadius: 8 }} />}
          </div>
        </>
      )}
      <div className="field" style={{ marginTop: 12 }}>
        <label className="field__label" htmlFor="unMessage">給管理員的說明（選填）</label>
        <textarea id="unMessage" className="input" rows={3} maxLength={1000} value={message}
          onChange={(event) => setMessage(event.target.value)} />
      </div>
      {error && <p className="field__error" role="alert">{error}</p>}
      <div className="dialog__actions">
        <button type="button" className="btn" onClick={onClose} disabled={submit.isPending}>取消</button>
        <button type="button" className="btn btn--primary" disabled={submit.isPending}
          onClick={() => { setError(''); submit.mutate() }}>
          {submit.isPending ? '送出中…' : '送出申請'}
        </button>
      </div>
      <ImageCropDialog file={cropping} aspect={4 / 3} outputWidth={1280} title="裁切新的信件照片"
        hint="拖曳照片移動位置，用滑桿或滾輪放大縮小；虛線框內就是願望詳細頁會顯示的範圍。"
        onCancel={() => setCropping(null)}
        onConfirm={(cropped) => { setLetter(cropped); setCropping(null) }} />
    </Dialog>
  )
}
