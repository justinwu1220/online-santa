import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight, MessageCircle } from 'lucide-react'
import { useEffect, useState } from 'react'
import { ConversationForm, ConversationLog } from '../../components/console/ConversationLog'
import { Drawer } from '../../components/console/Drawer'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { ApiError, api } from '../../lib/api'
import { pageTitle } from '../../lib/brand'
import { formatDateTime } from '../../lib/format'
import type { AdminTeamThread, TeamMessageView } from '../../lib/types'

/**
 * 團隊對話（設計稿的管理員後台沒有這一頁；機構後台的「與團隊對話」需要有人回覆，所以補上）：
 * 左邊是有對話的機構（未讀的排前面、紅色數字），點一條開抽屜看完整對話並回覆。
 * 打開對話時把機構的訊息標為已讀，選單的紅色數字跟著歸零。
 */
export function AdminTeamChat() {
  const threads = useQuery({
    queryKey: ['admin-team-chat'],
    queryFn: () => api.get<AdminTeamThread[]>('/api/admin/team-chat'),
    refetchInterval: 60_000,
  })
  const [openId, setOpenId] = useState<string | null>(null)

  useEffect(() => {
    document.title = pageTitle('團隊對話｜管理員後台')
    return () => { document.title = pageTitle() }
  }, [])

  const list = threads.data ?? []
  const open = list.find((thread) => thread.organizationId === openId) ?? null

  return (
    <section className="org-tab" aria-labelledby="adChatTitle">
      <div className="org-head"><h1 id="adChatTitle">團隊對話</h1></div>
      <p className="set-sec__desc" style={{ margin: 0 }}>
        機構在機構後台的「與團隊對話」傳來的訊息會出現在這裡，回覆後機構會在選單看到新訊息。
      </p>

      {threads.isLoading && <Spinner label="載入對話" />}
      {threads.isError && <ErrorNotice error={threads.error} onRetry={() => void threads.refetch()} />}
      {threads.data && (
        <div className="org-list" role="list">
          {list.length === 0 ? <p className="org-empty">還沒有機構傳訊息來。</p> : list.map((thread) => (
            <button key={thread.organizationId} type="button" className="org-row" role="listitem"
              aria-label={`${thread.organizationName}${thread.unread ? `，${thread.unread} 則新訊息` : ''}`}
              onClick={() => setOpenId(thread.organizationId)}>
              <span className="org-row__title">{thread.organizationName}
                <span className="org-row__sub">{thread.lastFromTeam ? '團隊：' : ''}{thread.lastBody}</span>
              </span>
              <span className="org-row__who">{formatDateTime(thread.lastAt)}</span>
              <span className="org-row__badges">
                {thread.unread > 0 && (
                  <span className="badge badge--msg">
                    <MessageCircle className="icon" aria-hidden />{thread.unread} 則新訊息
                  </span>
                )}
              </span>
              <ChevronRight className="icon org-row__go" aria-hidden />
            </button>
          ))}
        </div>
      )}

      <ThreadDrawer thread={open} onClose={() => setOpenId(null)} />
    </section>
  )
}

function ThreadDrawer({ thread, onClose }: { thread: AdminTeamThread | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  // 留住最後一條對話，關閉動畫期間內容不會消失
  const [shown, setShown] = useState<AdminTeamThread | null>(thread)
  if (thread && thread !== shown) setShown(thread)
  const current = thread ?? shown
  const orgId = thread?.organizationId ?? null

  const messages = useQuery({
    queryKey: ['admin-team-chat', orgId],
    queryFn: () => api.get<TeamMessageView[]>(`/api/admin/team-chat/${orgId}`),
    enabled: orgId !== null,
  })

  // 打開對話就把機構的訊息標為已讀
  const unread = thread?.unread ?? 0
  useEffect(() => {
    if (!orgId || unread === 0) return
    api.post(`/api/admin/team-chat/${orgId}/read`)
      .then(() => Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin-team-chat'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-team-unread'] }),
      ]))
      .catch(() => undefined)
  }, [orgId, unread, queryClient])

  const reply = useMutation({
    mutationFn: (body: string) => api.post<TeamMessageView>(`/api/admin/team-chat/${orgId}`, { body }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin-team-chat'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-team-unread'] }),
      ])
    },
  })

  if (!current) return null
  return (
    <Drawer open={orgId !== null} onClose={onClose} labelledBy="adChatDrawerTitle" title={current.organizationName}>
      <div className="drawer__body">
        {messages.isLoading && <Spinner label="載入對話" />}
        {messages.isError && <ErrorNotice error={messages.error} onRetry={() => void messages.refetch()} />}
        {messages.data && (
          <ConversationLog messages={messages.data} counterpart={current.organizationName}
            viewer="team" className="team-chat__log" label={`與${current.organizationName}的對話`} />
        )}
        <ConversationForm placeholder={`回覆${current.organizationName}…`} rows={3}
          busy={reply.isPending}
          error={reply.isError ? (reply.error instanceof ApiError ? reply.error.detail : '傳送失敗，請稍後再試') : null}
          onSend={async (text) => { await reply.mutateAsync(text) }} />
      </div>
    </Drawer>
  )
}
