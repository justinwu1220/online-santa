import { AlertTriangle } from 'lucide-react'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../lib/authContext'
import { effectiveRoleOf, useCurrentUser } from '../lib/useCurrentUser'
import type { UserRole } from '../lib/types'
import { Callout } from './ui/Callout'
import { ErrorNotice, Spinner } from './ui/Spinner'

/**
 * 角色守衛。
 *
 * 三種狀態都要處理好，否則會出現很糟的閃爍：身分還沒查回來時顯示 spinner，
 * 不能先閃一次「無權限」；查不到身分（後端掛了）要說出來，不能靜靜地擋住。
 *
 * 未登入一律導到 `/login`，登入後再回到原本要去的地方。
 *
 * @param wrongRole 角色不符時的行為。後台用 `redirect` 送回願望牆——不能送回登入頁，
 *                  登入頁看到已登入的人會導回 next，兩邊會互相導向形成無限重導。
 *                  主網站用預設的 `explain`——機構成員來看自己的認領頁並沒有走錯入口
 */
export function RequireRole({ role, wrongRole = 'explain', children }: {
  role: UserRole
  wrongRole?: 'explain' | 'redirect'
  children: ReactNode
}) {
  const { email } = useAuth()
  const me = useCurrentUser()
  const location = useLocation()

  if (!email) {
    // 帶上原本要去的地方，登入後才回得來
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?next=${next}`} replace />
  }

  if (me.isLoading) {
    return <Shell><Spinner label="確認身分" /></Shell>
  }

  if (me.isError) {
    return (
      <Shell><ErrorNotice error={me.error} onRetry={() => void me.refetch()} /></Shell>
    )
  }

  // 以實際生效的角色判斷，與後端的 AppPrincipal.effectiveRole() 對齊
  const actual = effectiveRoleOf(me.data)

  if (actual !== role) {
    if (wrongRole === 'redirect') {
      return <Navigate to="/" replace />
    }
    return <WrongRole actual={actual} expected={role} />
  }

  return <>{children}</>
}

const ROLE_LABELS: Record<UserRole, string> = {
  DONOR: '一般民眾',
  ORG_MEMBER: '機構成員',
  ADMIN: '平台管理員',
}

/** 守衛擋在各個版型外面，自己要帶深色背景。 */
function Shell({ children }: { children: ReactNode }) {
  return <div className="rd"><main className="page">{children}</main></div>
}

function WrongRole({ actual, expected }: { actual?: UserRole; expected: UserRole }) {
  return (
    <Shell>
      <Callout tone="warn" icon={<AlertTriangle className="icon" aria-hidden />}>
        <strong>這個區域僅限{ROLE_LABELS[expected]}。</strong>
        你目前的身分是{actual ? ROLE_LABELS[actual] : '未知'}。
        如果這不是你預期的，請確認登入的帳號是否正確。
      </Callout>
    </Shell>
  )
}
