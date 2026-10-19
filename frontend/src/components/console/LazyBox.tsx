import { useState, type ReactNode } from 'react'
import { rise } from '../../lib/motion'
import { ErrorNotice } from '../ui/Spinner'

/**
 * 「點擊載入」卡片（設計稿 16.4）：對話、附件、捐贈收據、機構照片與文件預設不載入。
 * 標題、右上「載入○○」按鈕、說明為什麼敏感，並以粗體提醒載入會另外寫入一筆稽核紀錄。
 * 按下後才打 API（後端那一次呼叫就是稽核寫入點）、內容淡入、按鈕變「已載入」。
 *
 * @param secret 「僅管理員」標籤（捐贈收據這類只有管理員看得到的內容）
 */
export function LazyBox({ title, desc, button, secret, load, children }: {
  title: string
  desc: string
  button: string
  secret?: boolean
  /** 載入資料；回傳值交給 children 渲染 */
  load: () => Promise<unknown>
  children: (data: never) => ReactNode
}) {
  const [state, setState] = useState<{ status: 'idle' | 'loading' | 'loaded' | 'error'; data?: unknown; error?: unknown }>(
    { status: 'idle' })

  async function run() {
    setState({ status: 'loading' })
    try {
      const data = await load()
      setState({ status: 'loaded', data })
    } catch (error) {
      setState({ status: 'error', error })
    }
  }

  return (
    <section className={`lazy${secret ? ' lazy--secret' : ''}${state.status === 'loaded' ? ' is-loaded' : ''}`}>
      <div className="lazy__head">
        <h3>{title}</h3>
        <button type="button" className="btn btn--sm" disabled={state.status === 'loading' || state.status === 'loaded'}
          onClick={() => void run()}>
          {state.status === 'loaded' ? '已載入' : state.status === 'loading' ? '載入中…' : button}
        </button>
      </div>
      <p className="lazy__desc">{desc}<strong>載入會另外寫入一筆稽核紀錄。</strong></p>
      <div className="lazy__body" ref={(node) => {
        if (node && state.status === 'loaded') rise([node], { delay: 0, stagger: 0, dist: 6, dur: 260 })
      }}>
        {state.status === 'error' && <ErrorNotice error={state.error} onRetry={() => void run()} />}
        {state.status === 'loaded' && (children as (data: unknown) => ReactNode)(state.data)}
      </div>
    </section>
  )
}
