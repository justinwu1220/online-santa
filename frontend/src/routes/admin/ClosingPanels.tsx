import { useState, type ReactNode } from 'react'
import { ReasonDialog } from '../../components/console/ReasonDialog'
import { Pager } from '../../components/ui/Pager'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { formatPlainDate } from '../../lib/format'
import type { CampaignView, MailProgress, MailTarget, PdfStatus, ReceiptStatus, ReceiptView } from '../../lib/types'
import { useCheckGate } from './CheckGate'
import {
  downloadAllReceipts, downloadClosingPackage, openReceipt, previewMail, sendMail, useReceipts, useVoidReceipt,
  type MailKind, type ReceiptFilter,
} from './closingQueries'

/**
 * 年度營運頁的「檔期設定」與「收據」分頁。
 *
 * 願望完成時系統會自動開立收據（配號、存資料）並產生三聯單 PDF；這裡讓管理員設定檔期、
 * 查看與預覽收據、作廢重開與全部下載；下載、產生結案文件與寄信前會先跳出收據檢查彈窗。
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
        <dt>願望上架期間</dt><dd>{period(campaign.wishListStart, campaign.wishListEnd)}</dd>
        <dt>收據編號</dt>
        <dd>已開立 {campaign.receiptsIssued} 張，下一張 {campaign.nextReceiptNo}</dd>
      </dl>
    </section>
  )
}

type Chip = { key: string; label: string; status?: ReceiptStatus; pdfStatus?: PdfStatus }

const CHIPS: Chip[] = [
  { key: 'all', label: '全部' },
  { key: 'issued', label: '有效', status: 'ISSUED' },
  { key: 'voided', label: '已作廢', status: 'VOIDED' },
  { key: 'failed', label: 'PDF 失敗', status: 'ISSUED', pdfStatus: 'FAILED' },
]

const PDF_LABEL: Record<PdfStatus, string> = {
  PENDING: '待產生', READY: '已產生', FAILED: 'PDF 失敗',
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

  const [exportProgress, setExportProgress] = useState<string>()
  const checkGate = useCheckGate(campaign.id)

  async function open(receipt: ReceiptView, kind: 'RED' | 'WHITE_YELLOW') {
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

  return (
    <section className="panel set-sec" aria-label="收據">
      <div className="panel__head" style={{ marginBottom: 0 }}>
        <h2 className="panel__title">收據</h2>
        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          <button type="button" className="link-btn" onClick={checkGate.view}>檢視收據檢查</button>
          <button type="button" className="btn btn--sm" disabled={exportProgress !== undefined}
            onClick={() => checkGate.gate('下載', () => void runExport())}>
            {exportProgress ?? '全部下載'}
          </button>
        </div>
      </div>
      <div className="closing-actions">
        <MailBlock campaignId={campaign.id} kind="receipts" title="紅聯收據寄給捐贈人"
          hint="只寄給填了「要收據」且有信箱的捐贈人" />
      </div>
      {checkGate.dialog}
      {notice && <div className={`callout callout--${notice.tone}`} role="status"><p>{notice.text}</p></div>}
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

      <ReasonDialog open={voiding !== null} wide title="作廢並重開收據"
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
  onOpen: (receipt: ReceiptView, kind: 'RED' | 'WHITE_YELLOW') => void
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
          {receipt.giftQuantity} {receipt.giftUnit}，{receipt.giftValue.toLocaleString('zh-TW')} 元，收到日 {formatPlainDate(receipt.donatedOn)}
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
          {([['RED', '紅聯'], ['WHITE_YELLOW', '白黃聯']] as const).map(([kind, label]) => (
            <button key={kind} type="button" className="btn btn--sm" disabled={voided} aria-label={`預覽${label}`}
              onClick={() => onOpen(receipt, kind)}>{label}</button>
          ))}
          <button type="button" className="btn btn--sm" disabled={voided} onClick={() => onVoid(receipt)}>作廢重開</button>
        </div>
      </td>
    </tr>
  )
}

/**
 * 結案文件分頁：產生結案文件包（成果備查與結案備查併報），以及把 05A＋05B 寄給受贈機構。
 * 寄信先「寄送前檢查」（不寄信）看會寄給誰、幾封，再按「確認寄出」；已寄的不會重寄，失敗的可另按「重寄失敗」。
 */
export function ClosingDocsPanel({ campaign }: { campaign: CampaignView }) {
  return (
    <section className="panel set-sec" aria-label="結案文件">
      <div className="panel__head" style={{ marginBottom: 0 }}>
        <h2 className="panel__title">結案文件</h2>
      </div>
      <p className="set-sec__desc" style={{ margin: 0 }}>
        一次產生官方 Excel、核章 PDF、成果報告、05 表單、照表操作頁、收據與待補資料；產生完成後可把 05A＋05B 寄給受贈機構。
        收據的預覽、下載與寄給捐贈人的紅聯在「收據」分頁。
      </p>
      <div className="closing-actions">
        <ClosingPackageSection campaignId={campaign.id} />
        <MailBlock campaignId={campaign.id} kind="org-forms" title="機構確認表（05A＋05B）寄給受贈機構"
          hint="每個受贈機構一封，附 05A、05B；康乃心自己的 05C 不寄。05B 內沒有任何個人資料。" />
      </div>
    </section>
  )
}

function MailBlock({ campaignId, kind, title, hint, children }: {
  campaignId: string; kind: MailKind; title: string; hint: string; children?: ReactNode
}) {
  const [progress, setProgress] = useState<MailProgress>()
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ tone: 'ok' | 'warn'; text: string }>()
  const checkGate = useCheckGate(campaignId)

  async function run(action: () => Promise<MailProgress>, done: (p: MailProgress) => string) {
    setBusy(true)
    setMessage(undefined)
    try {
      const p = await action()
      setProgress(p)
      setMessage({ tone: p.stopped || p.failed > 0 ? 'warn' : 'ok', text: done(p) })
    } catch (e) {
      setMessage({ tone: 'warn', text: e instanceof Error ? e.message : '操作失敗，請稍後再試' })
    } finally {
      setBusy(false)
    }
  }

  const sendText = (p: MailProgress) => {
    if (p.stopped === 'NOT_CONFIGURED') return '寄信功能尚未設定，沒有寄出任何信。'
    if (p.stopped === 'LIMIT') return `今天的寄信額度用完了，已寄 ${p.sent} 封，還有 ${p.pending} 封明天再按「確認寄出」續寄。`
    return `已寄出 ${p.sent} 封${p.failed > 0 ? `，${p.failed} 封失敗（可按「重寄失敗」）` : ''}。`
  }


  return (
    <div className="closing-card">
      <h3 className="closing-card__title">{title}</h3>
      <p className="closing-card__hint">{hint}</p>
      {children}
      <div className="closing-card__btns">
        <button type="button" className="btn btn--sm" disabled={busy}
          onClick={() => void run(() => previewMail(campaignId, kind), (p) => `檢查完成：待寄 ${p.pending} 封。`)}>
          寄送前檢查
        </button>
        {progress && progress.pending > 0 && (
          <button type="button" className="btn btn--sm btn--primary" disabled={busy}
            onClick={() => checkGate.gate('寄出', () => void run(() => sendMail(campaignId, kind, false, setProgress), sendText))}>
            {busy ? '寄送中…' : `確認寄出 ${progress.pending} 封`}
          </button>
        )}
        {progress && progress.failed > 0 && (
          <button type="button" className="btn btn--sm" disabled={busy}
            onClick={() => void run(() => sendMail(campaignId, kind, true, setProgress), sendText)}>
            重寄失敗（{progress.failed} 封）
          </button>
        )}
      </div>
      {progress && (
        <p className="closing-card__stats">
          應寄 {progress.total}　已寄 {progress.sent}　待寄 {progress.pending}　失敗 {progress.failed}
          {progress.notReady > 0 && `　PDF 尚未備妥 ${progress.notReady}`}
          {progress.noEmail > 0 && `　沒有信箱 ${progress.noEmail}`}
          {progress.dailyLimit > 0 && `　（每日寄信上限 ${progress.dailyLimit} 封）`}
        </p>
      )}
      {progress && progress.targets.length > 0 && (
        <MailTargetTable kind={kind} targets={progress.targets} total={progress.total} />
      )}
      {message && <div className={`callout callout--${message.tone}`} role="status"><p>{message.text}</p></div>}
      {checkGate.dialog}
    </div>
  )
}

const MAIL_STATUS: Record<MailTarget['status'], { label: string; tone: string }> = {
  SENT: { label: '已寄', tone: 'open' },
  PENDING: { label: '待寄', tone: 'pending' },
  FAILED: { label: '寄送失敗', tone: 'late' },
  NO_EMAIL: { label: '沒有信箱', tone: 'returned' },
  NOT_READY: { label: 'PDF 尚未備妥', tone: 'draft' },
}

/**
 * 寄送前檢查的明細名單：這次會寄給誰、寄到哪個信箱、到哪一步了。
 * 紅聯是一張收據一列（只列勾了「要收據」的），機構確認表是一個受贈機構一列。
 * 寄送中也是同一份名單，所以每寄完一段就看得到狀態往前走。
 */
function MailTargetTable({ kind, targets, total }: { kind: MailKind; targets: MailTarget[]; total: number }) {
  const receipts = kind === 'receipts'
  const more = total - targets.length
  return (
    <>
      {more > 0 && (
        <p className="closing-card__hint">名單只列前 {targets.length} 筆；其餘 {more} 筆的數量以上面那行為準。</p>
      )}
      <div className="table-wrap mail-targets">
        <table className="audit">
          <thead>
            <tr>
              {receipts && <th scope="col">收據編號</th>}
              <th scope="col">{receipts ? '捐贈者' : '機構'}</th>
              <th scope="col">寄到</th>
              <th scope="col">狀況</th>
            </tr>
          </thead>
          <tbody>
            {targets.map((target) => (
              <tr key={target.id}>
                {receipts && <td>{target.reference}</td>}
                <td style={{ whiteSpace: 'normal' }}>{target.name}</td>
                <td style={{ whiteSpace: 'normal' }}>{target.recipient ?? '—'}</td>
                <td style={{ whiteSpace: 'normal' }}>
                  <span className="st-chip" data-s={MAIL_STATUS[target.status].tone}>{MAIL_STATUS[target.status].label}</span>
                  {target.error && <><br /><span style={{ color: 'var(--fg-muted)' }}>{target.error}</span></>}
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
 * 結案文件包：產生官方 Excel、核章 PDF、成果報告、05 表單、照表操作頁、收據與待補資料，打包成一個壓縮檔。
 * 成果備查與結案備查一律併報，不分階段。沒有的資料（簽章、用印、個人實際領受…）文件上寫「待補」，
 * 清單在壓縮檔的 05_留存備份/待補資料.md。
 */
function ClosingPackageSection({ campaignId }: { campaignId: string }) {
  const [running, setRunning] = useState(false)
  const checkGate = useCheckGate(campaignId)
  const [progress, setProgress] = useState<string>()
  const [result, setResult] = useState<{ tone: 'ok' | 'warn'; text: string }>()

  async function run() {
    setResult(undefined)
    setRunning(true)
    setProgress('準備中…')
    try {
      const view = await downloadClosingPackage(campaignId, (v) => {
        setProgress(v.status === 'PREPARING' ? `準備與打包中…（${v.prepared}/${v.total}）` : '準備下載…')
      })
      setResult({
        tone: view.skipped > 0 ? 'warn' : 'ok',
        text: '已下載結案文件包。請先看壓縮檔裡 05_留存備份/待補資料.md，那裡列出還缺哪些資料；簽章與用印都是留白的。'
          + (view.skipped > 0 ? ` 另有 ${view.skipped} 張收據因 PDF 無法產生沒有收進去。` : ''),
      })
    } catch (e) {
      setResult({ tone: 'warn', text: e instanceof Error ? e.message : '產生失敗，請稍後再試' })
    } finally {
      setProgress(undefined)
      setRunning(false)
    }
  }

  return (
    <div className="closing-card">
      <h3 className="closing-card__title">產生結案文件</h3>
      <p className="closing-card__hint">
        產生官方 Excel、核章 PDF、成果報告、05 表單、照表操作頁、收據與待補資料，打包成一個壓縮檔下載。
        成果備查與結案備查一律併報，資料截止日是檔期的財物使用結束日。
      </p>
      <div className="closing-card__btns">
        <button type="button" className="btn btn--sm btn--primary" disabled={running}
          onClick={() => checkGate.gate('產生結案文件包', () => void run())}>
          {running ? progress : '產生結案文件包'}
        </button>
      </div>
      {result && <div className={`callout callout--${result.tone}`} role="status" style={{ flexBasis: '100%' }}><p>{result.text}</p></div>}
      {checkGate.dialog}
    </div>
  )
}
