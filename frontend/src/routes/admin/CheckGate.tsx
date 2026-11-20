import { useState, type ReactNode } from 'react'
import { Dialog } from '../../components/ui/Dialog'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import type { ReceiptCheck } from '../../lib/types'
import { useReceiptCheck } from './closingQueries'

/**
 * 收據檢查的結果摘要：已完成的願望、有效收據、PDF 狀態與問題清單。
 * 「需處理」是錯誤、「提醒」不影響繼續。只顯示，不修改任何資料。
 */
export function ReceiptCheckSummary({ check }: { check: ReceiptCheck }) {
  const errors = check.issues.filter((item) => item.severity === 'ERROR')
  const warnings = check.issues.filter((item) => item.severity === 'WARNING')
  return (
    <div className={`callout callout--${errors.length > 0 ? 'warn' : 'ok'}`} role="status" aria-label="收據檢查結果">
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
    <Dialog open={open} onClose={onClose} labelledBy="ck-title">
      <h2 id="ck-title">收據檢查</h2>
      {mode === 'gate' && !check.isError && (
        <p>{`${actionLabel}之前先確認收據狀況，沒問題就按「繼續${actionLabel}」。`}</p>
      )}
      {check.isFetching && !check.data && <Spinner label="檢查收據中" />}
      {check.isError && <ErrorNotice error={check.error} onRetry={() => void check.refetch()} />}
      {check.data && !check.isError && <ReceiptCheckSummary check={check.data} />}
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
