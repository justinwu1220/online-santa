import { useMutation } from '@tanstack/react-query'
import { Check, Clock, Copy } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { api } from '../../lib/api'
import { formatDate, formatDateTime } from '../../lib/format'
import type { AttachmentView, ClaimDonorView } from '../../lib/types'
import { Dialog } from '../../components/ui/Dialog'
import { FormField, Input } from '../../components/ui/Fields'
import { ErrorNotice } from '../../components/ui/Spinner'
import { useToast } from '../../components/ui/toastContext'

/** 步驟面板：編號圓章（完成後變綠底打勾）＋標題。 */
export function StepPanel({ no, done, title, action, children }: {
  no: number
  done?: boolean
  title: string
  action?: ReactNode
  children: ReactNode
}) {
  return (
    <section className="panel">
      <div className="panel__head">
        <span className={`step-no${done ? ' is-done' : ''}`}>
          {done ? <Check className="icon" aria-hidden /> : no}
        </span>
        <h2 className="panel__title">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

/**
 * 單欄複製按鈕（SPEC 11.2）。先用 Clipboard API，失敗（舊版 Safari、App 內建瀏覽器）
 * 改用隱藏文字框＋execCommand。複製後圖示變成綠色勾 1.6 秒，並提示「已複製{欄位名}」。
 */
function CopyButton({ text, label }: { text: string; label: string }) {
  const toast = useToast()
  const [copied, setCopied] = useState(false)

  async function copy() {
    let ok = false
    try {
      await navigator.clipboard.writeText(text)
      ok = true
    } catch {
      const box = document.createElement('textarea')
      box.value = text
      box.style.position = 'fixed'
      box.style.opacity = '0'
      document.body.appendChild(box)
      box.select()
      try { ok = document.execCommand('copy') } catch { ok = false }
      box.remove()
    }

    if (ok) {
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1600)
      toast.show(`已複製${label}`)
    } else {
      toast.show('複製失敗，請手動選取文字', 'danger')
    }
  }

  return (
    <button type="button" className={`copy-btn${copied ? ' is-copied' : ''}`}
      aria-label={`複製${label}`} onClick={() => void copy()}>
      {copied ? <Check className="icon" aria-hidden /> : <Copy className="icon" aria-hidden />}
    </button>
  )
}

/**
 * 寄送地址。
 *
 * 這是捐贈者唯一看得到機構地址的地方——願望牆與願望詳情只有機構名稱。認領之後才需要
 * 知道寄去哪，那道界線由後端的擁有者檢查守著，不要把地址加進公開的願望視圖。
 */
export function AddressPanel({ claim, done }: { claim: ClaimDonorView; done: boolean }) {
  const hasAddress = Boolean(claim.organizationAddress)

  return (
    <StepPanel no={1} done={done} title="把禮物寄到這裡">
      {hasAddress ? (
        <>
        <dl className="addr">
          <div>
            <dt>收件單位</dt>
            {/* 只放名稱、不加「收」字，才能直接貼進網購的收件人欄位 */}
            <dd>{claim.organizationName}<CopyButton text={claim.organizationName} label="收件單位" /></dd>
          </div>
          {claim.organizationPreferredDeliveryTime && (
            <div>
              <dt>方便收件時間</dt>
              <dd>{claim.organizationPreferredDeliveryTime}</dd>
            </div>
          )}
          <div className="addr__full">
            <dt>地址</dt>
            {/* 這是要照著抄去寄件的資訊，給它最高的對比 */}
            <dd>{claim.organizationAddress}<CopyButton text={claim.organizationAddress!} label="地址" /></dd>
          </div>
          {claim.organizationPhone && (
            <div>
              <dt>聯絡電話</dt>
              <dd>{claim.organizationPhone}<CopyButton text={claim.organizationPhone} label="聯絡電話" /></dd>
            </div>
          )}
          {/* 兩支各自判斷而不是二選一：機構兩個都填了就兩個都給——市話找不到人時
              手機是唯一的補救，而這個面板的用途就是「把禮物寄到」需要的全部資訊 */}
          {claim.organizationMobilePhone && (
            <div>
              <dt>行動電話</dt>
              <dd>
                {claim.organizationMobilePhone}
                <CopyButton text={claim.organizationMobilePhone} label="行動電話" />
              </dd>
            </div>
          )}
        </dl>
        <ul className="tips">
          <li>收件人請填上方的收件單位，請勿選擇超商取貨或貨到付款。</li>
          <li>包裹外或訂單備註請寫「線上聖誕老人」與孩子暱稱「{claim.childAlias}」，方便機構核對。</li>
          <li>包裹裡不要放你的電話、地址或社群帳號；祝福卡片署名用暱稱就好。</li>
        </ul>
        </>
      ) : (
        // 更早之前建立的機構可能連地址都沒有
        <div className="notice notice--warn">這個機構還沒有填寫收件地址，請用下方的訊息詢問機構。</div>
      )}
    </StepPanel>
  )
}

/** 已回報寄送的摘要（物流、追蹤碼、禮物、數量、價格、日期、照片、是否索取收據）。 */
export function ShipSummary({ claim, proofs }: {
  claim: ClaimDonorView
  proofs: AttachmentView[]
}) {
  return (
    <StepPanel no={2} done title="已回報寄送">
      <dl className="ship-summary">
        <div><dt>物流業者</dt><dd>{claim.trackingCarrier}</dd></div>
        <div><dt>追蹤碼</dt><dd>{claim.trackingNumber}</dd></div>
        {claim.giftDescription && (
          <div><dt>實際寄出的禮物</dt><dd>{claim.giftDescription}</dd></div>
        )}
        {claim.giftQuantity != null && <div><dt>數量</dt><dd>{claim.giftQuantity} 份</dd></div>}
        {claim.giftPrice != null && (
          <div><dt>禮物價格</dt><dd>{claim.giftPrice.toLocaleString('zh-TW')} 元</dd></div>
        )}
        {claim.shippedOn && <div><dt>寄出日期</dt><dd>{claim.shippedOn.replaceAll('-', '/')}</dd></div>}
        {/* 照片放在「寄件照片」底下，整列寬；回報送出後只能檢視，不能刪除或補傳 */}
        <div className="c-full">
          <dt>寄件照片</dt>
          <dd>
            {proofs.length} 張
            {proofs.length > 0 && (
              <div style={{ marginTop: 10 }}><ProofGrid photos={proofs} emptyHint="" /></div>
            )}
          </dd>
        </div>
        <div>
          <dt>捐贈收據</dt>
          <dd>{claim.receiptRequested ? '需要（已提供開據資料）' : '不需要另外索取'}</dd>
        </div>
      </dl>
      {/* 機構確認收到之後就不再提示修改 */}
      {claim.status === 'SHIPPED' && (
        <p className="ship-note">資料需要修改的話，請在下方對話告訴機構或寫信給我們。</p>
      )}
    </StepPanel>
  )
}

/**
 * 照片格線。`deletable` 只在「這組照片是目前使用者自己上傳的」時才開——
 * 機構的回饋照片對捐贈者是唯讀的。
 */
export function ProofGrid({ photos, emptyHint, deletable, onChanged }: {
  photos: AttachmentView[]
  emptyHint: string
  deletable?: boolean
  onChanged?: () => void
}) {
  if (photos.length === 0) {
    return emptyHint ? <p style={{ margin: 0, fontSize: 14, color: 'var(--fg-muted)' }}>{emptyHint}</p> : null
  }
  return (
    <div className="proof-grid">
      {photos.map((photo) => (
        <ProofTile key={photo.id} photo={photo} deletable={deletable} onChanged={onChanged} />
      ))}
    </div>
  )
}

function ProofTile({ photo, deletable, onChanged }: {
  photo: AttachmentView
  deletable?: boolean
  onChanged?: () => void
}) {
  const [confirming, setConfirming] = useState(false)
  const remove = useMutation({
    mutationFn: () => api.delete(`/api/attachments/${photo.id}`),
    onSuccess: () => onChanged?.(),
  })

  return (
    <div className="proof">
      <a href={photo.url} target="_blank" rel="noreferrer">
        <img src={photo.url} alt="" />
      </a>
      {deletable && !confirming && (
        <button type="button" className="proof__del" onClick={() => setConfirming(true)}>刪除</button>
      )}
      {confirming && (
        <div className="proof__confirm">
          <span>刪除後無法復原</span>
          {remove.isError && <span style={{ color: '#fca5a5' }}>刪除失敗，請再試一次</span>}
          <div style={{ display: 'flex', gap: 6 }}>
            <button type="button" className="btn btn--sm btn--danger-solid" disabled={remove.isPending}
              onClick={() => remove.mutate()}>
              {remove.isPending ? '刪除中…' : '確定刪除'}
            </button>
            <button type="button" className="btn btn--sm btn--ghost" onClick={() => setConfirming(false)}>
              取消
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

/** 「你的進度」四步（SPEC 11.1）：認領、寄出、機構收到、完成，各步下方顯示時間。 */
export function ProgressSteps({ claim }: { claim: ClaimDonorView }) {
  const progress = claim.status === 'CLAIMED' ? 0 : claim.status === 'SHIPPED' ? 1
    : claim.status === 'RECEIVED' ? 2 : claim.status === 'COMPLETED' ? 3 : null

  if (progress === null) {
    return (
      <div className="progress">
        <p className="progress__label">你的進度</p>
        <div className="notice notice--done" style={{ fontSize: 14 }}>
          {claim.status === 'RELEASED' ? '機構已收回這筆認領。' : '你已取消這筆認領。'}
          {claim.releaseReason && <p>原因：{claim.releaseReason}</p>}
        </div>
      </div>
    )
  }

  const steps = [
    { label: '認領願望', at: claim.claimedAt },
    { label: '寄出禮物', at: claim.shippedAt },
    { label: '機構確認收到', at: claim.receivedAt },
    { label: '流程完成', at: claim.completedAt },
  ]

  return (
    <div className="progress">
      <p className="progress__label">你的進度</p>
      <ol className="steps">
        {steps.map((step, index) => {
          const done = index <= progress
          const current = !done && index === progress + 1
          return (
            <li key={step.label} className={`step${done ? ' is-done' : ''}${current ? ' is-current' : ''}`}>
              <span className="step__dot"><Check className="icon" aria-hidden /></span>
              <span className="step__label">
                {step.label}
                {done && step.at && <span className="step__time">{formatDateTime(step.at)}</span>}
              </span>
            </li>
          )
        })}
      </ol>
    </div>
  )
}

/** 期限提示：待寄送時黃色（逾期紅色）＋「還有 N 天」膠囊；寄出後改綠色說明目前狀態。 */
export function DeadlineBar({ claim, remaining }: { claim: ClaimDonorView; remaining: number | null }) {
  if (claim.status === 'CLAIMED') {
    const late = remaining !== null && remaining < 0
    return (
      <div className="deadline" data-tone={late ? 'late' : undefined}>
        <Clock className="icon" aria-hidden />
        <p className="deadline__text" style={{ margin: 0 }}>
          {late ? (
            <>
              <strong>已逾期 {Math.abs(remaining!)} 天</strong>
              <small>機構可能會收回這個願望讓其他人認領——想繼續的話請盡快寄出並回報，或與機構聯繫。</small>
            </>
          ) : (
            <>
              <strong>請於 {formatDate(claim.shipDeadlineAt)} 前寄出禮物</strong>
              <small>寄出後記得回來回報，孩子才知道禮物在路上了。</small>
            </>
          )}
        </p>
        {remaining !== null && !late && <span className="deadline__days">還有 {remaining} 天</span>}
        {late && <span className="deadline__days">逾期</span>}
      </div>
    )
  }

  const copy = claim.status === 'SHIPPED'
    ? ['已回報寄送', '等機構確認收到禮物，機構收到後會更新進度。']
    : claim.status === 'RECEIVED'
      ? ['機構已收到禮物', '機構正在確認，完成後這筆認領就結案了。']
      : claim.status === 'COMPLETED'
        ? ['流程完成', '謝謝你，孩子收到了你的心意。']
        : null
  if (!copy) return null

  return (
    <div className="deadline" data-tone="ok">
      <Check className="icon" aria-hidden />
      <p className="deadline__text" style={{ margin: 0 }}>
        <strong>{copy[0]}</strong>
        <small>{copy[1]}</small>
      </p>
    </div>
  )
}

/** 取消認領：只在待寄送時出現；先確認，原因會記在歷程裡、機構看得到。 */
export function CancelPanel({ claimId, onCancelled }: { claimId: string; onCancelled: () => void }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')

  const cancel = useMutation({
    mutationFn: () => api.post(`/api/claims/${claimId}/cancel`,
      reason.trim() ? { reason: reason.trim() } : undefined),
    onSuccess: () => { setOpen(false); onCancelled() },
  })

  return (
    <section className="panel cancel-panel">
      <h2>不方便繼續了？</h2>
      <p>取消後這個願望會立刻回到願望牆，讓其他人有機會認領。</p>
      <button type="button" className="btn btn--danger" onClick={() => setOpen(true)}>取消認領</button>

      <Dialog open={open} onClose={() => !cancel.isPending && setOpen(false)} dismissible={!cancel.isPending}
        labelledBy="cancel-title">
        <h2 id="cancel-title">確定要取消認領嗎？</h2>
        <p>取消後願望會立刻回到願望牆，這個動作無法復原。</p>
        <FormField label="取消原因" optional hint="會記錄在歷程中，機構看得到">
          <Input maxLength={255} value={reason} onChange={(event) => setReason(event.target.value)} />
        </FormField>
        {cancel.isError && <div style={{ marginTop: 12 }}><ErrorNotice error={cancel.error} /></div>}
        <div className="dialog__actions">
          <button type="button" className="btn btn--ghost" disabled={cancel.isPending}
            onClick={() => setOpen(false)}>
            再想想
          </button>
          <button type="button" className="btn btn--danger-solid" disabled={cancel.isPending}
            onClick={() => cancel.mutate()}>
            {cancel.isPending ? '處理中…' : '確定取消認領'}
          </button>
        </div>
      </Dialog>
    </section>
  )
}
