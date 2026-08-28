import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../lib/authContext'
import { useCurrentUser } from '../lib/useCurrentUser'
import type { UserRole } from '../lib/types'
import { ErrorBanner, Notice, Spinner } from './Feedback'

/**
 * 角色守衛。
 *
 * 三種狀態都要處理好，否則會出現很糟的閃爍：身分還沒查回來時顯示 spinner，
 * 不能先閃一次「無權限」；查不到身分（後端掛了）要說出來，不能靜靜地擋住。
 *
 * @param loginPath 未登入時導向哪個登入頁。三個區域各有自己的入口
 */
export function RequireRole({ role, loginPath, children }: {
  role: UserRole
  loginPath: string
  children: ReactNode
}) {
  const { email } = useAuth()
  const me = useCurrentUser()
  const location = useLocation()

  if (!email) {
    // 帶上原本要去的地方，登入後才回得來
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`${loginPath}?next=${next}`} replace />
  }

  if (me.isLoading) {
    return <Spinner label="確認身分" />
  }

  if (me.isError) {
    return (
      <div className="mx-auto max-w-lg py-12">
        <ErrorBanner error={me.error} onRetry={() => void me.refetch()} />
      </div>
    )
  }

  if (me.data?.role !== role) {
    return <WrongRole actual={me.data?.role} expected={role} />
  }

  return <>{children}</>
}

const ROLE_LABELS: Record<UserRole, string> = {
  DONOR: '一般民眾',
  ORG_MEMBER: '機構成員',
  ADMIN: '平台管理員',
}

function WrongRole({ actual, expected }: { actual?: UserRole; expected: UserRole }) {
  return (
    <div className="mx-auto max-w-lg py-12">
      <Notice tone="warning">
        <p className="font-medium">這個區域僅限{ROLE_LABELS[expected]}</p>
        <p className="mt-1">
          你目前的身分是{actual ? ROLE_LABELS[actual] : '未知'}。
          如果這不是你預期的，請確認登入的帳號是否正確。
        </p>
      </Notice>
    </div>
  )
}
