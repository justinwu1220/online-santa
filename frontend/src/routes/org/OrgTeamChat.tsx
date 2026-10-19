import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Send, TriangleAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { ConversationLog } from '../../components/console/ConversationLog'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { ApiError, api } from '../../lib/api'
import { pageTitle } from '../../lib/brand'
import type { TeamMessageView } from '../../lib/types'

/** 常用問題快捷鈕（設計稿 TEAM_QUICKS）：點了把問題放進輸入框，後面補一個冒號讓機構接著寫。 */
const QUICKS = ['禮物還沒到，想請你們協助', '想修改已上架的願望', '孩子已經離開機構', '收據與領受證明的問題']

/**
 * 與團隊對話（設計稿 15.6）：機構與「線上聖誕老人團隊」的對話，與任何認領無關；
 * 和認領者的對話在各個願望的抽屜裡。有常用問題快捷鈕與紅框的「緊急：收到可疑物品」。
 * 抽屜裡的「回報異常」會帶著預填的文字來到這裡。
 */
export function OrgTeamChat() {
  const queryClient = useQueryClient()
  const location = useLocation()
  const prefill = (location.state as { prefill?: string } | null)?.prefill ?? ''
  const [text, setText] = useState(prefill)

  const messages = useQuery({
    queryKey: ['org-team-chat'],
    queryFn: () => api.get<TeamMessageView[]>('/api/organizations/me/team-chat'),
  })

  useEffect(() => {
    document.title = pageTitle('與團隊對話｜機構後台')
    return () => { document.title = pageTitle() }
  }, [])

  // 打開這一頁就把團隊傳來的訊息標為已讀（選單的紅色數字歸零）
  const hasUnread = messages.data?.some((message) => message.fromTeam && !message.read)
  useEffect(() => {
    if (!hasUnread) return
    api.post('/api/organizations/me/team-chat/read')
      .then(() => queryClient.invalidateQueries({ queryKey: ['org-unread'] }))
      .catch(() => undefined)
  }, [hasUnread, queryClient])

  const send = useMutation({
    mutationFn: (body: string) => api.post<TeamMessageView>('/api/organizations/me/team-chat', { body }),
    onSuccess: async () => {
      setText('')
      await queryClient.invalidateQueries({ queryKey: ['org-team-chat'] })
    },
  })

  function submit() {
    const body = text.trim()
    if (body && !send.isPending) send.mutate(body)
  }

  return (
    <section className="org-tab" aria-labelledby="orgChatTitle">
      <div className="org-head"><h1 id="orgChatTitle">與團隊對話</h1></div>
      <div className="panel team-chat">
        <div className="team-chat__who">
          <span className="team-chat__avatar" aria-hidden>
            <img src="/assets/team-santa.png" alt="" width={44} height={44} />
          </span>
          <div><strong>線上聖誕老人團隊</strong></div>
        </div>
        <p className="team-chat__note">任何問題都可以問我們。和認領者的對話在各個願望裡。</p>

        {messages.isLoading && <Spinner label="載入對話" />}
        {messages.isError && <ErrorNotice error={messages.error} onRetry={() => void messages.refetch()} />}
        {messages.data && (
          <ConversationLog messages={messages.data} counterpart="線上聖誕老人團隊"
            className="team-chat__log" label="與團隊的對話" />
        )}

        <div className="quicks" aria-label="常用問題">
          {QUICKS.map((quick) => (
            <button key={quick} type="button" className="quick" onClick={() => {
              setText(`${quick}：`)
              document.getElementById('teamChatInput')?.focus()
            }}>{quick}</button>
          ))}
        </div>

        <form className="chat-form" onSubmit={(event) => { event.preventDefault(); submit() }}>
          <label className="sr-only" htmlFor="teamChatInput">輸入訊息</label>
          <textarea id="teamChatInput" className="input" rows={3} maxLength={2000} value={text}
            placeholder="輸入想問團隊的問題…" onChange={(event) => setText(event.target.value)}
            onKeyDown={(event) => {
              if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') { event.preventDefault(); submit() }
            }} />
          {send.isError && (
            <span className="field__error" role="alert">
              {send.error instanceof ApiError ? send.error.detail : '傳送失敗，請稍後再試'}
            </span>
          )}
          <div className="chat-form__row team-chat__row">
            <button type="button" className="btn btn--danger btn--sm" onClick={() => {
              setText('緊急：收到可疑物品，請協助處理。')
              document.getElementById('teamChatInput')?.focus()
            }}>
              <TriangleAlert className="icon" aria-hidden />
              緊急：收到可疑物品
            </button>
            <button type="submit" className="btn" disabled={!text.trim() || send.isPending}>
              <Send className="icon" aria-hidden />
              傳送
            </button>
          </div>
        </form>
      </div>
    </section>
  )
}
