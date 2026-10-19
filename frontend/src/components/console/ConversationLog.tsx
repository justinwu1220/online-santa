import { Send } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { formatDateTime } from '../../lib/format'
import type { MessageView, TeamMessageView } from '../../lib/types'

/** 對話內容（設計稿 `.chat`）：自己靠右、對方靠左，系統通知是置中的虛線框。 */
export function ConversationLog({ messages, counterpart, empty = '還沒有對話。', className = '', label }: {
  messages: (MessageView | TeamMessageView)[]
  /** 對方的稱呼，顯示在對方訊息上方 */
  counterpart: string
  empty?: string
  className?: string
  label?: string
}) {
  const box = useRef<HTMLDivElement>(null)
  // 有新訊息就捲到最底
  useEffect(() => {
    if (box.current) box.current.scrollTop = box.current.scrollHeight
  }, [messages.length])

  return (
    <div className={`chat ${className}`} role="log" aria-live="polite" aria-label={label} ref={box}>
      {messages.length === 0 && <p className="auth-note">{empty}</p>}
      {messages.map((message) => {
        if ('kind' in message && message.kind === 'SYSTEM') {
          return (
            <div key={message.id} className="msg msg--system">
              <span className="msg__text">{message.body}</span>
              <span className="msg__time">系統通知・{formatDateTime(message.sentAt)}</span>
            </div>
          )
        }
        const mine = 'fromTeam' in message ? !message.fromTeam : message.fromMe
        return (
          <div key={message.id} className={`msg ${mine ? 'msg--me' : 'msg--org'}`}>
            {!mine && <span className="msg__who">{counterpart}</span>}
            <span className="msg__text">{message.body}</span>
            <span className="msg__time">{formatDateTime(message.sentAt)}</span>
          </div>
        )
      })}
    </div>
  )
}

/** 輸入框與「傳送」（Ctrl／⌘＋Enter 也能送出）。 */
export function ConversationForm({ placeholder, busy, error, onSend, rows = 2, extra, initial = '' }: {
  placeholder: string
  busy?: boolean
  error?: string | null
  onSend: (text: string) => void | Promise<void>
  rows?: number
  /** 傳送鈕左邊的額外按鈕（團隊對話的「緊急」） */
  extra?: React.ReactNode
  initial?: string
}) {
  const [text, setText] = useState(initial)

  async function submit() {
    const value = text.trim()
    if (!value || busy) return
    await onSend(value)
    setText('')
  }

  return (
    <form className="chat-form" onSubmit={(event) => { event.preventDefault(); void submit() }}>
      <label className="sr-only" htmlFor="conversation-input">輸入訊息</label>
      <textarea id="conversation-input" className="input" rows={rows} maxLength={2000} value={text}
        placeholder={placeholder} onChange={(event) => setText(event.target.value)}
        onKeyDown={(event) => {
          if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
            event.preventDefault()
            void submit()
          }
        }} />
      {error && <span className="field__error" role="alert">{error}</span>}
      <div className={`chat-form__row${extra ? ' team-chat__row' : ''}`}>
        {extra}
        <button type="submit" className="btn btn--sm" disabled={!text.trim() || busy}>
          <Send className="icon" aria-hidden />
          傳送
        </button>
      </div>
    </form>
  )
}
