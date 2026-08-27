import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api } from '../lib/api'
import { formatDateTime } from '../lib/format'
import type { MessageView } from '../lib/types'
import { ErrorBanner, Spinner } from './Feedback'
import { Button, TextArea } from './Form'

/**
 * 捐贈者與機構在單一認領內的對話。
 *
 * 打開時標記對方的訊息為已讀——後端刻意把已讀做成獨立端點而非在 GET 時順手處理，
 * 所以由前端在「真的打開對話」時呼叫。
 */
export function MessageThread({ claimId, closed }: { claimId: string; closed: boolean }) {
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
    return <ErrorBanner error={messages.error} onRetry={() => void messages.refetch()} />
  }

  return (
    <div className="space-y-4">
      <div className="max-h-96 space-y-3 overflow-y-auto pr-1">
        {messages.data?.length === 0 && (
          <p className="py-6 text-center text-sm text-slate-400">
            還沒有任何訊息。有任何寄送上的問題都可以在這裡討論。
          </p>
        )}
        {messages.data?.map((message) => (
          <div key={message.id}
            className={`flex ${message.fromMe ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-[80%] rounded-2xl px-4 py-2 ${
              message.fromMe
                ? 'bg-santa-600 text-white'
                : 'bg-white text-slate-700 ring-1 ring-santa-100'
            }`}>
              <p className="whitespace-pre-wrap text-sm">{message.body}</p>
              <p className={`mt-1 text-[11px] ${
                message.fromMe ? 'text-santa-100' : 'text-slate-400'
              }`}>
                {formatDateTime(message.sentAt)}
              </p>
            </div>
          </div>
        ))}
      </div>

      {closed ? (
        <p className="rounded-lg bg-slate-50 px-4 py-3 text-sm text-slate-500">
          這筆認領已經結束，無法再傳送訊息。
        </p>
      ) : (
        <form
          className="space-y-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (draft.trim()) send.mutate(draft.trim())
          }}
        >
          <TextArea
            rows={3}
            value={draft}
            maxLength={2000}
            placeholder="輸入訊息…"
            onChange={(event) => setDraft(event.target.value)}
          />
          {send.isError && <ErrorBanner error={send.error} />}
          <div className="flex justify-end">
            <Button type="submit" disabled={!draft.trim() || send.isPending}>
              {send.isPending ? '傳送中…' : '傳送'}
            </Button>
          </div>
        </form>
      )}
    </div>
  )
}
