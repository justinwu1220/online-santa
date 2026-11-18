import { useEffect, useState } from 'react'
import { ReasonDialog } from '../../components/console/ReasonDialog'
import { Pager } from '../../components/ui/Pager'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { pageTitle } from '../../lib/brand'
import { formatPlainDate } from '../../lib/format'
import type { CampaignView, PdfStatus, ReceiptStatus, ReceiptView } from '../../lib/types'
import { CampaignDialog } from './CampaignDialog'
import {
  openReceipt, useCampaigns, useGenerateReceiptPdfs, useIssueMissingReceipts, useReceipts, useVoidReceipt,
  type ReceiptFilter,
} from './closingQueries'

/**
 * 結案文件：活動檔期設定與捐贈收據。
 *
 * 願望完成時系統會自動開立收據（配號、存資料）並產生三聯單 PDF；這裡讓管理員設定檔期、
 * 查看與下載收據、作廢重開，以及在檔期晚於願望完成、或許可文號晚於收據開立時補開、補產。
 */
export function AdminClosing() {
  useEffect(() => {
    document.title = pageTitle('結案文件｜管理員後台')
    return () => { document.title = pageTitle() }
  }, [])

  const campaigns = useCampaigns()
  const [selectedId, setSelectedId] = useState<string>()
  const [editing, setEditing] = useState<CampaignView | 'new' | null>(null)
  const current = campaigns.data?.find((item) => item.id === selectedId) ?? campaigns.data?.[0]

  return (
    <section className="org-tab" aria-labelledby="adClosingTitle">
      <div className="org-head">
        <h1 id="adClosingTitle">結案文件</h1>
        <button type="button" className="btn btn--sm" onClick={() => setEditing('new')}>新增活動檔期</button>
      </div>

      {campaigns.isLoading && <Spinner label="載入活動檔期" />}
      {campaigns.isError && <ErrorNotice error={campaigns.error} onRetry={() => void campaigns.refetch()} />}
      {campaigns.data && campaigns.data.length === 0 && (
        <p className="org-empty">
          還沒有活動檔期。建立檔期後，之後完成的願望才會自動開立收據；先前完成的願望可以在檔期建立後補開。
        </p>
      )}
      {campaigns.data && current && (
        <>
          <CampaignPanel campaign={current} all={campaigns.data} onSelect={setSelectedId}
            onEdit={() => setEditing(current)} />
          <ReceiptsPanel campaign={current} />
        </>
      )}
      <CampaignDialog campaign={editing} onClose={() => setEditing(null)} />
    </section>
  )
}

function period(start: string, end: string) {
  return `${formatPlainDate(start)} 至 ${formatPlainDate(end)}`
}

function CampaignPanel({ campaign, all, onSelect, onEdit }: {
  campaign: CampaignView
  all: CampaignView[]
  onSelect: (id: string) => void
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
        <div style={{ display: 'flex', gap: 8 }}>
          {all.length > 1 && (
            <select className="input" aria-label="選擇檔期" value={campaign.id}
              onChange={(event) => onSelect(event.target.value)}>
              {all.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          )}
          <button type="button" className="btn btn--sm" onClick={onEdit}>修改</button>
        </div>
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

function ReceiptsPanel({ campaign }: { campaign: CampaignView }) {
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

  async function download(receipt: ReceiptView, kind: 'WHITE' | 'YELLOW_RED') {
    setDownloadError(undefined)
    try {
      await openReceipt(receipt.id, kind)
    } catch (e) {
      setDownloadError(e instanceof Error ? e.message : '開啟失敗，請稍後再試')
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
        「補產 PDF」為還沒有 PDF 的收據補產，填入許可文號後按這個。收據 PDF 含捐贈者證號，每次開啟都會寫入稽核。
      </p>
      {(issueMissing.isError || generate.isError) && <ErrorNotice error={issueMissing.error ?? generate.error} />}
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
                  <ReceiptRow key={receipt.id} receipt={receipt} onDownload={download} onVoid={setVoiding} />
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

function ReceiptRow({ receipt, onDownload, onVoid }: {
  receipt: ReceiptView
  onDownload: (receipt: ReceiptView, kind: 'WHITE' | 'YELLOW_RED') => void
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
        <button type="button" className="btn btn--sm" disabled={voided} onClick={() => onDownload(receipt, 'WHITE')}>白聯</button>{' '}
        <button type="button" className="btn btn--sm" disabled={voided} onClick={() => onDownload(receipt, 'YELLOW_RED')}>黃紅聯</button>{' '}
        <button type="button" className="btn btn--sm" disabled={voided} onClick={() => onVoid(receipt)}>作廢重開</button>
      </td>
    </tr>
  )
}
