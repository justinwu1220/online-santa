import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Send } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { api } from '../../lib/api'
import { useRiseLastOnGrow } from '../../lib/useRiseLastOnGrow'
import { formatDateTime } from '../../lib/format'
import type { MessageView } from '../../lib/types'
import { TextArea } from './Fields'
import { ErrorNotice, Spinner } from './Spinner'

/**
 * 捐贈者與機構在單一認領內的對話（SPEC 11.2：對方靠左、自己靠右；Ctrl／⌘＋Enter 送出）。
 *
 * 打開時標記對方的訊息為已讀——後端刻意把已讀做成獨立端點而非在 GET 時順手處理，
 * 所以由前端在「真的打開對話」時呼叫。
 *
 * @param counterpart 對方的稱呼，顯示在對方訊息上方（認領者看到「機構」，機構看到「認領者」）
 */
export function Chat({ claimId, closed, counterpart }: {
  claimId: string
  closed: boolean
  counterpart: string
}) {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState('')

  const messages = useQuery({
    queryKey: ['claim', claimId, 'messages'],
    queryFn: () => api.get<MessageView[]>(`/api/claims/${claimId}/messages`),
  })

  const markRead = useMutation({
    mutationFn: () => api.post(`/api/claims/${claimId}/messages/mark-read`),
    onSuccess: () => {
      // 未讀數顯示在認領清單上，標記後要一起更新
      void queryClient.invalidateQueries({ queryKey: ['claims'] })
    },
  })

  const send = useMutation({
    mutationFn: (body: string) =>
      api.post<MessageView>(`/api/claims/${claimId}/messages`, { body }),
    onSuccess: () => {
      setDraft('')
      void queryClient.invalidateQueries({ queryKey: ['claim', claimId, 'messages'] })
    },
  })

  const logRef = useRef<HTMLDivElement>(null)
  useRiseLastOnGrow(logRef, messages.data?.length)

  const hasUnread = messages.data?.some((message) => !message.fromMe && !message.read)

  useEffect(() => {
    if (hasUnread && !markRead.isPending) {
      markRead.mutate()
    }
    // 只在出現未讀時觸發一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasUnread])

  if (messages.isLoading) return <Spinner label="載入對話" />
  if (messages.isError) {
    return <ErrorNotice error={messages.error} onRetry={() => void messages.refetch()} />
  }

  function submit() {
    if (draft.trim() && !send.isPending) send.mutate(draft.trim())
  }

  return (
    <div>
      <div className="chat" aria-live="polite" ref={logRef}>
        {messages.data?.length === 0 && (
          <p style={{ margin: 0, padding: '18px 0', textAlign: 'center', fontSize: 14, color: 'var(--fg-muted)' }}>
            還沒有任何訊息。有任何寄送上的問題都可以在這裡討論。
          </p>
        )}
        {messages.data?.map((message) => (
          <div key={message.id} className={`msg ${message.fromMe ? 'msg--me' : 'msg--org'}`}>
            {!message.fromMe && <span className="msg__who">{counterpart}</span>}
            {message.body}
            <span className="msg__time">{formatDateTime(message.sentAt)}</span>
          </div>
        ))}
      </div>

      {closed ? (
        <div className="notice notice--done" style={{ fontSize: 14 }}>
          這筆認領已經結束，無法再傳送訊息。
        </div>
      ) : (
        <form
          className="chat-form"
          onSubmit={(event) => { event.preventDefault(); submit() }}
        >
          <TextArea
            rows={3}
            value={draft}
            maxLength={2000}
            placeholder="輸入訊息…"
            aria-label="訊息內容"
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                event.preventDefault()
                submit()
              }
            }}
          />
          {send.isError && <ErrorNotice error={send.error} />}
          <div className="chat-form__row">
            <button type="submit" className="btn" disabled={!draft.trim() || send.isPending}>
              <Send className="icon" aria-hidden />
              {send.isPending ? '傳送中…' : '傳送'}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
