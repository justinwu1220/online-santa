import { useState, type ReactNode } from 'react'
import { Dialog } from '../../components/ui/Dialog'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import type { PdfStatus, ReceiptCheck, ReceiptCheckIssue, ReceiptView } from '../../lib/types'
import { useReceiptCheck, useReceiptRoster } from './closingQueries'

interface Tag { label: string; tone: string }

const PDF_TAG: Record<PdfStatus, Tag> = {
  READY: { label: 'PDF 已產生', tone: 'open' },
  PENDING: { label: 'PDF 待產生', tone: 'pending' },
  FAILED: { label: 'PDF 無法產生', tone: 'late' },
}

/** 名單上要標的問題。PDF 狀態自己有標籤，這裡不重複標。 */
const ISSUE_TAG: Record<string, string> = {
  MISSING_ID_NUMBER: '缺證號',
  NO_EVIDENCE: '無發票佐證',
}

/** 勾了「要收據」的那幾張，紅聯寄到哪一步了。 */
function redTag(receipt: ReceiptView): Tag | null {
  if (!receipt.receiptRequested || receipt.status === 'VOIDED') return null
  if (receipt.redSentAt) return { label: '紅聯已寄', tone: 'open' }
  if (receipt.redSendError) return { label: '紅聯寄送失敗', tone: 'late' }
  if (!receipt.donorEmail) return { label: '沒有信箱', tone: 'draft' }
  return { label: '紅聯待寄', tone: 'pending' }
}

function tagsFor(receipt: ReceiptView, issues: ReceiptCheckIssue[]): Tag[] {
  const tags: Tag[] = []
  if (receipt.status === 'VOIDED') {
    tags.push({ label: '已作廢', tone: 'draft' })
  } else {
    tags.push(PDF_TAG[receipt.pdfStatus])
    const red = redTag(receipt)
    if (red) tags.push(red)
  }
  for (const issue of issues) {
    const label = ISSUE_TAG[issue.code]
    if (label) tags.push({ label, tone: issue.severity === 'ERROR' ? 'returned' : 'draft' })
  }
  return tags
}

/**
 * 檢查結果下面的明細名單：本檔期每一張收據一列（含作廢），右邊標出這張的狀況與問題——
 * 問題是用收據編號跟上面的問題清單對起來的，兩邊同一份資料。
 *
 * <p>已完成卻沒開立收據的願望沒有列（它沒有收據編號），留在上面的問題清單裡。
 */
function ReceiptRoster({ campaignId, issues, open }: {
  campaignId: string; issues: ReceiptCheckIssue[]; open: boolean
}) {
  const roster = useReceiptRoster(campaignId, open)

  const byNo = new Map<string, ReceiptCheckIssue[]>()
  for (const issue of issues) {
    if (!issue.receiptNo) continue
    byNo.set(issue.receiptNo, [...(byNo.get(issue.receiptNo) ?? []), issue])
  }

  if (roster.isLoading) return <Spinner label="載入收據名單" />
  if (roster.isError) return <ErrorNotice error={roster.error} onRetry={() => void roster.refetch()} />
  if (!roster.data) return null

  const rows = roster.data.content
  const more = roster.data.totalElements - rows.length
  return (
    <>
      <p className="ck-roster__head">
        收據名單（{roster.data.totalElements} 張，含作廢）
        {more > 0 && `：這裡只列前 ${rows.length} 張，其餘請看收據分頁的清單`}
      </p>
      <div className="table-wrap ck-roster">
        <table className="audit">
          <thead>
            <tr>
              <th scope="col">編號</th>
              <th scope="col">捐贈者</th>
              <th scope="col">物資</th>
              <th scope="col">機構</th>
              <th scope="col">狀況</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr><td colSpan={5} style={{ color: 'var(--fg-muted)' }}>這個檔期還沒有收據。</td></tr>
            ) : rows.map((receipt) => (
              <tr key={receipt.id}>
                <td>{receipt.receiptNo}</td>
                <td style={{ whiteSpace: 'normal' }}>
                  {receipt.donorName}
                  {receipt.receiptRequested && <span className="st-chip" data-s="open" style={{ marginLeft: 6 }}>要收據</span>}
                </td>
                <td style={{ whiteSpace: 'normal' }}>
                  {receipt.giftDescription}
                  <br />
                  <span style={{ color: 'var(--fg-muted)' }}>
                    {receipt.giftQuantity} {receipt.giftUnit}，{receipt.giftValue.toLocaleString('zh-TW')} 元
                  </span>
                </td>
                <td style={{ whiteSpace: 'normal' }}>{receipt.organizationName}</td>
                <td style={{ whiteSpace: 'normal' }}>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {tagsFor(receipt, byNo.get(receipt.receiptNo) ?? []).map((tag) => (
                      <span key={tag.label} className="st-chip" data-s={tag.tone}>{tag.label}</span>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

/**
 * 收據檢查的結果摘要：已完成的願望、有效收據、PDF 狀態與問題清單。
 * 「需處理」是錯誤、「提醒」不影響繼續。只顯示，不修改任何資料。
 */
export function ReceiptCheckSummary({ check }: { check: ReceiptCheck }) {
  const errors = check.issues.filter((item) => item.severity === 'ERROR')
  const warnings = check.issues.filter((item) => item.severity === 'WARNING')
  return (
    <div className={`callout callout--stack callout--${errors.length > 0 ? 'warn' : 'ok'}`} role="status" aria-label="收據檢查結果">
      <p>
        <strong>{errors.length > 0 ? `收據檢查：有 ${errors.length} 項需處理` : '收據檢查：沒有需處理的項目'}</strong>
        {warnings.length > 0 && `，另有 ${warnings.length} 項提醒`}
      </p>
      <p>
        已完成的願望 {check.completedWishes} 筆、有效收據 {check.validReceipts} 張（作廢 {check.voidedReceipts} 張），
        時價合計 {check.totalValue.toLocaleString('zh-TW')} 元。
        PDF：已產生 {check.pdfReady}、待產生 {check.pdfPending}、失敗 {check.pdfFailed}。
        全部下載的壓縮檔裡，紅聯與白黃聯資料夾各 {check.pdfReady} 檔。
      </p>
      {check.issues.length > 0 && (
        <ul style={{ margin: '8px 0 0', paddingLeft: 20 }}>
          {check.issues.map((item, index) => (
            <li key={index}>
              <span className="st-chip" data-s={item.severity === 'ERROR' ? 'returned' : 'draft'} style={{ marginRight: 6 }}>
                {item.severity === 'ERROR' ? '需處理' : '提醒'}
              </span>
              {item.receiptNo && <strong>{item.receiptNo}：</strong>}{item.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * 動作前的收據檢查彈窗。開啟時自動檢查，顯示結果，在彈窗內確認才繼續。
 *
 * - `gate`：有「取消」與「繼續…」。有需處理的錯誤時，繼續鈕改成警示色的「仍要繼續（N 項需處理）」；
 *   檢查失敗（網路等）時顯示重試，仍可略過檢查繼續。
 * - `view`：只看結果，只有「關閉」。
 */
function CheckGateDialog({ campaignId, open, mode, actionLabel, onContinue, onClose }: {
  campaignId: string
  open: boolean
  mode: 'gate' | 'view'
  actionLabel: string
  onContinue: () => void
  onClose: () => void
}) {
  const check = useReceiptCheck(campaignId, open)
  const errors = check.data ? check.data.issues.filter((item) => item.severity === 'ERROR').length : 0

  let continueText = `繼續${actionLabel}`
  if (check.isFetching) continueText = '檢查中…'
  else if (check.isError) continueText = `略過檢查，仍要繼續${actionLabel}`
  else if (errors > 0) continueText = `仍要繼續${actionLabel}（${errors} 項需處理）`

  return (
    <Dialog open={open} onClose={onClose} labelledBy="ck-title" className="dialog dialog--wide">
      <h2 id="ck-title">收據檢查</h2>
      {mode === 'gate' && !check.isError && (
        <p>{`${actionLabel}之前先確認收據狀況，沒問題就按「繼續${actionLabel}」。`}</p>
      )}
      {check.isFetching && !check.data && <Spinner label="檢查收據中" />}
      {check.isError && <ErrorNotice error={check.error} onRetry={() => void check.refetch()} />}
      {check.data && !check.isError && <ReceiptCheckSummary check={check.data} />}
      {check.data && !check.isError && <ReceiptRoster campaignId={campaignId} issues={check.data.issues} open={open} />}
      <div className="dialog__actions">
        <button type="button" className="btn" onClick={onClose}>{mode === 'view' ? '關閉' : '取消'}</button>
        {mode === 'gate' && (
          <button type="button" className={`btn ${errors > 0 || check.isError ? 'btn--danger-solid' : 'btn--primary'}`}
            disabled={check.isFetching} onClick={onContinue}>
            {continueText}
          </button>
        )}
      </div>
    </Dialog>
  )
}

/**
 * 會用到收據資料的動作（下載、產生結案文件、寄信）前，先跳出收據檢查彈窗，確認後才執行。
 * 用法：{@code const g = useCheckGate(id)}，按鈕 onClick 改成 {@code g.gate('下載', () => run())}，
 * 並在畫面上放 {@code {g.dialog}}；{@code g.view()} 只開彈窗檢視結果、不做事。
 */
export function useCheckGate(campaignId: string): {
  gate: (actionLabel: string, action: () => void) => void
  view: () => void
  dialog: ReactNode
} {
  const [state, setState] = useState<{ open: boolean; mode: 'gate' | 'view'; label: string; action: (() => void) | null }>(
    { open: false, mode: 'gate', label: '', action: null })

  // 開啟時才掛載：同一個檔期的查詢結果會被快取，沒開的彈窗如果也渲染，同一個畫面會出現多份結果
  const dialog = !state.open ? null : (
    <CheckGateDialog campaignId={campaignId} open={state.open} mode={state.mode} actionLabel={state.label}
      onClose={() => setState((s) => ({ ...s, open: false }))}
      onContinue={() => {
        const action = state.action
        setState((s) => ({ ...s, open: false }))
        action?.()
      }} />
  )
  return {
    gate: (actionLabel, action) => setState({ open: true, mode: 'gate', label: actionLabel, action }),
    view: () => setState({ open: true, mode: 'view', label: '', action: null }),
    dialog,
  }
}
