import { useState } from 'react'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import type { FinanceData, FinanceMonth } from '../../lib/types'
import { useFinance, useSaveFinance } from './closingQueries'

type Field = 'cashIncome' | 'interest' | 'cashExpense'

/** 空白＝待補（null），0＝已核實沒有發生。 */
function parse(value: string): number | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : Math.max(0, Math.floor(Number(trimmed)))
}

const show = (value?: number | null) => (value === null || value === undefined ? '' : String(value))

/**
 * 檔期財務資料：每個月的現金實募、利息、募得款實支，以及自籌。系統沒有這些資料，由管理員手動填寫。
 * 結案文件上，空白的欄位寫「待補」，填 0 才代表已核實真的沒有發生——所以純物資的活動也不要預設填 0，
 * 要先向財務確認。
 */
export function FinancePanel({ campaignId }: { campaignId: string }) {
  const finance = useFinance(campaignId)
  const save = useSaveFinance(campaignId)
  const [draft, setDraft] = useState<FinanceData>()
  const [saved, setSaved] = useState(false)
  const data = draft ?? finance.data

  function edit(change: (previous: FinanceData) => FinanceData) {
    if (!data) return
    setDraft(change(data))
    setSaved(false)
  }

  function setMonth(index: number, field: Field | 'note', value: string) {
    edit((previous) => ({
      ...previous,
      months: previous.months.map((m, i): FinanceMonth => (i !== index ? m
        : field === 'note' ? { ...m, note: value } : { ...m, [field]: parse(value) })),
    }))
  }

  async function submit() {
    if (!data) return
    await save.mutateAsync(data)
    setDraft(undefined)
    setSaved(true)
  }

  return (
    <section className="panel set-sec" aria-label="財務資料">
      <div className="panel__head" style={{ marginBottom: 0 }}>
        <h2 className="panel__title">財務資料</h2>
        <button type="button" className="btn btn--sm btn--primary" disabled={!data || save.isPending || draft === undefined}
          onClick={() => void submit()}>
          {save.isPending ? '儲存中…' : '儲存'}
        </button>
      </div>
      <p className="set-sec__desc" style={{ margin: 0 }}>
        現金、利息、實支與自籌是系統沒有的資料，請向財務確認後填寫。<strong>空白代表「待補」</strong>，
        結案文件上會寫待補；填 <strong>0</strong> 才代表已核實真的沒有發生。純物資的活動也要先核實再填 0。
      </p>
      {finance.isLoading && <Spinner label="載入財務資料" />}
      {finance.isError && <ErrorNotice error={finance.error} onRetry={() => void finance.refetch()} />}
      {save.isError && <ErrorNotice error={save.error} />}
      {saved && <div className="callout callout--ok" role="status"><p>已儲存。</p></div>}

      {data && (
        <>
          <div style={{ overflowX: 'auto' }}>
            <table className="table" style={{ width: '100%' }}>
              <thead>
                <tr><th>年月</th><th>現金實募（元）</th><th>利息（元）</th><th>募得款實支（元）</th><th>備註</th></tr>
              </thead>
              <tbody>
                {data.months.map((m, index) => {
                  const label = `${m.year}/${String(m.month).padStart(2, '0')}`
                  return (
                    <tr key={label}>
                      <td>{label}</td>
                      {(['cashIncome', 'interest', 'cashExpense'] as const).map((field) => (
                        <td key={field}>
                          <input className="input" type="number" min={0} inputMode="numeric" placeholder="待補"
                            aria-label={`${label} ${field === 'cashIncome' ? '現金實募' : field === 'interest' ? '利息' : '募得款實支'}`}
                            value={show(m[field])} onChange={(event) => setMonth(index, field, event.target.value)} />
                        </td>
                      ))}
                      <td>
                        <input className="input" maxLength={300} aria-label={`${label} 備註`} value={m.note ?? ''}
                          onChange={(event) => setMonth(index, 'note', event.target.value)} />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '160px 1fr', gap: 8, alignItems: 'center' }}>
            <label htmlFor="selfFundedAmount">本會自籌金額（元）</label>
            <input id="selfFundedAmount" className="input" type="number" min={0} placeholder="待補" style={{ maxWidth: 200 }}
              value={show(data.selfFundedAmount)}
              onChange={(event) => edit((previous) => ({ ...previous, selfFundedAmount: parse(event.target.value) }))} />
            <label htmlFor="selfFundedNote">自籌說明</label>
            <input id="selfFundedNote" className="input" maxLength={500} value={data.selfFundedNote ?? ''}
              onChange={(event) => edit((previous) => ({ ...previous, selfFundedNote: event.target.value }))} />
          </div>
        </>
      )}
    </section>
  )
}
