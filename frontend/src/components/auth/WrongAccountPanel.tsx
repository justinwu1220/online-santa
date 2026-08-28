import { useAuth } from '../../lib/authContext'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import type { UserRole } from '../../lib/types'
import { EmailVerificationBanner } from '../EmailVerificationBanner'
import { Button } from '../Form'
import { Notice } from '../Feedback'

const ROLE_LABELS: Record<UserRole, string> = {
  DONOR: '一般民眾',
  ORG_MEMBER: '機構成員',
  ADMIN: '平台管理員',
}

/**
 * 「已經登入了，但這個帳號進不來」時，登入頁顯示的內容。
 *
 * 這是把後台入口擋在登入頁的另一半。少了它，使用者被導到登入頁卻只看到一段說明，
 * 想換帳號還得自己找地方登出——所以這裡一定要有「改用其他帳號登入」。
 *
 * 信箱未驗證是另一回事：帳號沒錯，只是還沒生效。那種情況要說的是「去收信」，
 * 而不是「換一個帳號」，否則使用者會以為自己註冊錯了。
 */
export function WrongAccountPanel({ expected, reason }: {
  expected: UserRole
  /** 覆寫預設說明，例如「管理員不得兼任機構」這種規則性的阻擋 */
  reason?: string
}) {
  const { email, signOut } = useAuth()
  const me = useCurrentUser()

  const unverified = me.data ? !me.data.emailVerified : false
  const actual = effectiveRoleOf(me.data)

  return (
    <div>
      {unverified && (
        <div className="mb-4 overflow-hidden rounded-lg border border-amber-200">
          <EmailVerificationBanner />
        </div>
      )}

      <Notice tone="warning">
        {unverified ? (
          <>
            <p className="font-medium">這個帳號還不能進入</p>
            <p className="mt-1">
              信箱驗證完成之前，帳號只有一般民眾的權限。請先完成上方的驗證步驟。
            </p>
          </>
        ) : (
          <>
            <p className="font-medium">這個帳號無法進入</p>
            <p className="mt-1">
              {reason ?? `這個區域僅限${ROLE_LABELS[expected]}，
                你目前的身分是${actual ? ROLE_LABELS[actual] : '未知'}。`}
            </p>
          </>
        )}
      </Notice>

      <p className="mt-4 truncate text-sm text-slate-500" title={email ?? ''}>
        目前登入：{email}
      </p>

      <Button className="mt-2 w-full" variant="secondary" onClick={() => void signOut()}>
        改用其他帳號登入
      </Button>
    </div>
  )
}
