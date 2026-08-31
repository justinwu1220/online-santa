import { useQuery } from '@tanstack/react-query'
import { Outlet } from 'react-router-dom'
import { api } from '../../lib/api'
import type { PlatformStats } from '../../lib/types'
import { ConsoleLayout } from '../../components/layouts/ConsoleLayout'

/**
 * 監控中心的外框。
 *
 * 主網站沒有任何連往這裡的連結——要進來只能直接輸入網址。這不是安全機制
 * （真正的保護是後端的 hasRole('ADMIN')），只是減少誤闖。
 *
 * logo 指向 /admin 而非 /：監控中心在使用者的感受上是獨立的系統，不該把人
 * 丟回主網站。
 */
export function AdminLayout() {
  // 待審核數放在導覽上，管理員一進來就看得到有事要處理
  const stats = useQuery({
    queryKey: ['admin-stats'],
    queryFn: () => api.get<PlatformStats>('/api/admin/stats'),
    staleTime: 30_000,
  })

  return (
    // subtitle 留給「這一個」的名稱（機構後台放機構名），平台名稱由 ConsoleLayout
    // 統一顯示。監控中心沒有對應的東西，所以不給
    <ConsoleLayout
      title="監控中心"
      accent="slate"
      homePath="/admin"
      items={[
        { to: '/admin', label: '總覽', end: true },
        { to: '/admin/organizations', label: '機構審核', badge: stats.data?.pendingOrganizations },
        { to: '/admin/wishes', label: '全站願望' },
        { to: '/admin/claims', label: '全站認領', badge: stats.data?.overdueClaims },
        { to: '/admin/system', label: '系統與稽核' },
      ]}
    >
      <Outlet />
    </ConsoleLayout>
  )
}
