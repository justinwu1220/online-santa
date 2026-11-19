import { useState } from 'react'
import { ReasonDialog } from '../../components/console/ReasonDialog'
import { Pager } from '../../components/ui/Pager'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { formatPlainDate } from '../../lib/format'
import type { CampaignView, PdfStatus, ReceiptCheck, ReceiptStatus, ReceiptView } from '../../lib/types'
import {
  downloadAllReceipts, openReceipt, useGenerateReceiptPdfs, useIssueMissingReceipts, useReceiptCheck, useReceipts, useVoidReceipt,
  type ReceiptFilter,
} from './closingQueries'

/**
 * 年度營運頁的「檔期設定」與「收據」分頁。
 *
 * 願望完成時系統會自動開立收據（配號、存資料）並產生三聯單 PDF；這裡讓管理員設定檔期、
 * 查看收據、作廢重開，以及在檔期晚於願望完成、或許可文號晚於收據開立時補開、補產。
 */

function period(start: string, end: string) {
  return `${formatPlainDate(start)} 至 ${formatPlainDate(end)}`
}

export function CampaignPanel({ campaign, onEdit }: {
  campaign: CampaignView
  onEdit: () => void
}) {
  return (
    <section className="panel set-sec" aria-label="活動檔期">
      <div className="panel__head">
        <h2 className="panel__title">
          活動檔期
          <span className="st-chip" data-s={campaign.status === 'ACTIVE' ? 'open' : 'draft'} style={{ marginLeft: 8 }}>
            {campaign.status === 'ACTIVE' ? '進行中' : '已結束'}
          </span>
        </h2>
        <button type="button" className="btn btn--sm" onClick={onEdit}>修改</button>
      </div>
      <dl className="set-sec__desc" style={{ margin: 0, display: 'grid', gridTemplateColumns: 'max-content 1fr', gap: '4px 16px' }}>
        <dt>檔期名稱</dt><dd>{campaign.name}</dd>
        <dt>活動編號</dt><dd>{campaign.activityNo ?? '—'}</dd>
        <dt>勸募許可文號</dt><dd>{campaign.permitNo ?? '尚未填寫'}</dd>
        <dt>勸募期間</dt><dd>{period(campaign.collectionStart, campaign.collectionEnd)}</dd>
        <dt>財物使用期間</dt><dd>{period(campaign.useStart, campaign.useEnd)}</dd>
        <dt>統計截止日</dt><dd>{campaign.cutoffDate ? formatPlainDate(campaign.cutoffDate) : '勸募結束日'}</dd>
        <dt>收據編號</dt>
        <dd>已開立 {campaign.receiptsIssued} 張，下一張 {campaign.nextReceiptNo}</dd>
      </dl>
      {!campaign.permitNo && (
        <div className="callout callout--warn" role="status">
          <p>
            尚未填寫勸募許可文號：願望完成時仍會開立收據（配號、存資料），但不會產生 PDF。
            取得文號後按「修改」填入，再到下方按「補產 PDF」。
          </p>
        </div>
      )}
    </section>
  )
}

type Chip = { key: string; label: string; status?: ReceiptStatus; pdfStatus?: PdfStatus }

const CHIPS: Chip[] = [
  { key: 'all', label: '全部' },
  { key: 'issued', label: '有效', status: 'ISSUED' },
  { key: 'voided', label: '已作廢', status: 'VOIDED' },
  { key: 'info', label: '待補許可文號', status: 'ISSUED', pdfStatus: 'PENDING_INFO' },
  { key: 'failed', label: 'PDF 失敗', status: 'ISSUED', pdfStatus: 'FAILED' },
]

const PDF_LABEL: Record<PdfStatus, string> = {
  PENDING: '待產生', PENDING_INFO: '待補許可文號', READY: '已產生', FAILED: 'PDF 失敗',
}

export function ReceiptsPanel({ campaign }: { campaign: CampaignView }) {
  const [chip, setChip] = useState('all')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [notice, setNotice] = useState<{ tone: 'ok' | 'warn'; text: string }>()
  const [downloadError, setDownloadError] = useState<string>()
  const [voiding, setVoiding] = useState<ReceiptView | null>(null)

  const selected = CHIPS.find((item) => item.key === chip) ?? CHIPS[0]
  const filter: ReceiptFilter = { status: selected.status, pdfStatus: selected.pdfStatus, q, page }
  const receipts = useReceipts(campaign.id, filter)
  const voidReceipt = useVoidReceipt()
  const issueMissing = useIssueMissingReceipts()
  const generate = useGenerateReceiptPdfs()

  const [exportProgress, setExportProgress] = useState<string>()
  const [checking, setChecking] = useState(false)
  const check = useReceiptCheck(campaign.id, checking)

  async function open(receipt: ReceiptView, kind: 'WHITE' | 'YELLOW_RED') {
    setDownloadError(undefined)
    try {
      await openReceipt(receipt.id, kind)
    } catch (e) {
      setDownloadError(e instanceof Error ? e.message : '開啟失敗，請稍後再試')
    }
  }

  async function runExport() {
    setDownloadError(undefined)
    setNotice(undefined)
    setExportProgress('準備中…')
    try {
      const result = await downloadAllReceipts(campaign.id, (view) => {
        setExportProgress(view.status === 'PREPARING'
          ? `準備 PDF 與打包中…（${view.prepared}/${view.total}）` : '準備下載…')
      })
      setNotice({
        tone: result.skipped > 0 ? 'warn' : 'ok',
        text: `已下載壓縮檔（${result.total - result.skipped} 張收據）`
          + (result.skipped > 0 ? `；另有 ${result.skipped} 張因 PDF 無法產生沒有收進去，原因寫在壓縮檔內的說明檔。` : '。'),
      })
    } catch (e) {
      setDownloadError(e instanceof Error ? e.message : '匯出失敗，請稍後再試')
    } finally {
      setExportProgress(undefined)
    }
  }

  async function runIssue() {
    const result = await issueMissing.mutateAsync(campaign.id)
    setNotice({
      tone: 'ok',
      text: result.done === 0 ? '沒有需要補開的收據。' : `已補開 ${result.done} 張收據，PDF 稍後自動產生。`,
    })
  }

  async function runGenerate() {
    const result = await generate.mutateAsync(campaign.id)
    setNotice({
      tone: result.pending > 0 ? 'warn' : 'ok',
      text: `已產生 ${result.done} 張收據 PDF`
        + (result.pending > 0 ? `，仍有 ${result.pending} 張待補或失敗（請看狀態欄）。` : '。'),
    })
  }

  return (
    <section className="panel set-sec" aria-label="收據">
      <div className="panel__head" style={{ marginBottom: 0 }}>
        <h2 className="panel__title">收據</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          <button type="button" className="btn btn--sm" disabled={check.isFetching}
            onClick={() => { if (checking) void check.refetch(); else setChecking(true) }}>
            {check.isFetching ? '檢查中…' : '收據檢查'}
          </button>
          <button type="button" className="btn btn--sm" disabled={exportProgress !== undefined}
            onClick={() => void runExport()}>
            {exportProgress ?? '全部下載'}
          </button>
          <button type="button" className="btn btn--sm" disabled={issueMissing.isPending}
            onClick={() => void runIssue()}>
            {issueMissing.isPending ? '補開中…' : '補開收據'}
          </button>
          <button type="button" className="btn btn--sm" disabled={generate.isPending}
            onClick={() => void runGenerate()}>
            {generate.isPending ? '產生中…' : '補產 PDF'}
          </button>
        </div>
      </div>
      <p className="set-sec__desc" style={{ margin: 0 }}>
        「補開收據」為已完成、寄出日落在勸募期間、卻還沒有收據的願望開立（依完成順序配號）；
        「補產 PDF」為還沒有 PDF 的收據補產，填入許可文號後按這個。點「白聯」「黃紅聯」在新分頁預覽；「全部下載」把有效收據的白聯與黃紅聯打包成一個壓縮檔。收據 PDF 含捐贈者證號，每次開啟或下載都會寫入稽核。
      </p>
      {(issueMissing.isError || generate.isError) && <ErrorNotice error={issueMissing.error ?? generate.error} />}
      {notice && <div className={`callout callout--${notice.tone}`} role="status"><p>{notice.text}</p></div>}
      {check.isError && <ErrorNotice error={check.error} onRetry={() => void check.refetch()} />}
      {check.data && <ReceiptCheckResult check={check.data} onClose={() => setChecking(false)} />}
      {downloadError && <div className="callout callout--warn" role="alert"><p>{downloadError}</p></div>}

      <div className="org-filters" role="tablist" aria-label="依收據狀態篩選">
        {CHIPS.map((item) => (
          <button key={item.key} type="button" role="tab" className="org-filter" aria-selected={item.key === chip}
            onClick={() => { setChip(item.key); setPage(0) }}>
            {item.label}
          </button>
        ))}
        <input className="input" type="search" placeholder="搜尋編號、捐贈者、機構" aria-label="搜尋收據"
          value={q} onChange={(event) => { setQ(event.target.value); setPage(0) }} style={{ marginLeft: 'auto', maxWidth: 260 }} />
      </div>

      {receipts.isLoading && <Spinner label="載入收據" />}
      {receipts.isError && <ErrorNotice error={receipts.error} onRetry={() => void receipts.refetch()} />}
      {receipts.data && (
        <>
          <div className="table-wrap">
            <table className="audit">
              <thead>
                <tr>
                  <th scope="col">編號</th>
                  <th scope="col">捐贈者</th>
                  <th scope="col">物資</th>
                  <th scope="col">機構</th>
                  <th scope="col">狀態</th>
                  <th scope="col">操作</th>
                </tr>
              </thead>
              <tbody>
                {receipts.data.content.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ color: 'var(--fg-muted)' }}>沒有符合的收據。</td>
                  </tr>
                ) : receipts.data.content.map((receipt) => (
                  <ReceiptRow key={receipt.id} receipt={receipt} onOpen={open} onVoid={setVoiding} />
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={receipts.data} onChange={setPage} />
        </>
      )}

      <ReasonDialog open={voiding !== null} title="作廢並重開收據"
        body={voiding ? `收據 ${voiding.receiptNo} 會作廢（號碼保留、不再配給別人），並用目前的認領資料重開一張新號碼的收據。` : ''}
        label="作廢原因" hint="會留在舊收據上，例如：捐贈者姓名填錯" minLength={2} tooShort="請至少寫 2 個字"
        okText="作廢並重開"
        onSubmit={async (reason) => {
          if (!voiding) return
          const replacement = await voidReceipt.mutateAsync({ id: voiding.id, reason })
          setNotice({ tone: 'ok', text: `已作廢 ${voiding.receiptNo}，重開為 ${replacement.receiptNo}。` })
          setVoiding(null)
        }}
        onClose={() => setVoiding(null)} />
    </section>
  )
}

function ReceiptRow({ receipt, onOpen, onVoid }: {
  receipt: ReceiptView
  onOpen: (receipt: ReceiptView, kind: 'WHITE' | 'YELLOW_RED') => void
  onVoid: (receipt: ReceiptView) => void
}) {
  const voided = receipt.status === 'VOIDED'
  return (
    <tr>
      <td>{receipt.receiptNo}</td>
      <td style={{ whiteSpace: 'normal' }}>
        {receipt.donorName}
        {receipt.receiptRequested && <span className="st-chip" data-s="open" style={{ marginLeft: 6 }}>要收據</span>}
      </td>
      <td style={{ whiteSpace: 'normal' }}>
        {receipt.giftDescription}
        <br />
        <span style={{ color: 'var(--fg-muted)' }}>
          {receipt.giftQuantity} {receipt.giftUnit}，{receipt.giftValue.toLocaleString('zh-TW')} 元，捐贈日 {formatPlainDate(receipt.donatedOn)}
        </span>
      </td>
      <td style={{ whiteSpace: 'normal' }}>{receipt.organizationName}</td>
      <td style={{ whiteSpace: 'normal' }}>
        {voided ? (
          <>
            <span className="st-chip" data-s="draft">已作廢</span>
            <br /><span style={{ color: 'var(--fg-muted)' }}>{receipt.voidReason}</span>
          </>
        ) : (
          <>
            <span className="st-chip" data-s={receipt.pdfStatus === 'READY' ? 'open' : receipt.pdfStatus === 'FAILED' ? 'late' : 'pending'}>
              {PDF_LABEL[receipt.pdfStatus]}
            </span>
            {receipt.pdfStatus === 'FAILED' && receipt.pdfError && (
              <><br /><span style={{ color: '#fca5a5' }}>{receipt.pdfError}</span></>
            )}
          </>
        )}
      </td>
      <td>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {([['WHITE', '白聯'], ['YELLOW_RED', '黃紅聯']] as const).map(([kind, label]) => (
            <button key={kind} type="button" className="btn btn--sm" disabled={voided} aria-label={`預覽${label}`}
              onClick={() => onOpen(receipt, kind)}>{label}</button>
          ))}
          <button type="button" className="btn btn--sm" disabled={voided} onClick={() => onVoid(receipt)}>作廢重開</button>
        </div>
      </td>
    </tr>
  )
}

function ReceiptCheckResult({ check, onClose }: { check: ReceiptCheck; onClose: () => void }) {
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
        PDF：已產生 {check.pdfReady}、待產生 {check.pdfPending}、待補許可文號 {check.pdfPendingInfo}、失敗 {check.pdfFailed}。
        全部下載的壓縮檔裡，白聯與黃紅聯資料夾各 {check.pdfReady} 檔。
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
      <p style={{ marginTop: 8 }}>
        <button type="button" className="btn btn--sm btn--ghost" onClick={onClose}>收起</button>
      </p>
    </div>
  )
}
