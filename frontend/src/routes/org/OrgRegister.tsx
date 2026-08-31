import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { describeAuthError, useAuth, usingFirebase } from '../../lib/authContext'
import { pageTitle } from '../../lib/brand'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import { WrongAccountPanel } from '../../components/auth/WrongAccountPanel'
import { ErrorBanner, Spinner } from '../../components/Feedback'
import { Button, Field, TextInput } from '../../components/Form'
import { StepHeading } from './applyShared'

/**
 * 機構申請的步驟一：建立帳號。
 *
 * <p>為什麼是兩頁而不是一張表單：帳號建立完之後一定要停下來。api 模組的驗證標頭是
 * AuthHeaderBridge 在 useEffect 裡登記的，要等 onAuthStateChanged 觸發重新 render 才
 * 更新；剛建完帳號就接著打 API，送出的可能是還沒帶 token 的舊標頭。密碼註冊還得再等
 * 使用者去點驗證信。既然中間本來就有一個斷點，把它做成兩頁比藏在一張表單裡誠實。
 *
 * <p>這一頁對未登入者開放。授權不靠它——POST /api/organizations 自己會擋下未驗證的
 * 信箱、已隸屬機構的人與管理員。
 */
export function OrgRegister() {
  const auth = useAuth()
  const me = useCurrentUser()

  const [accountMode, setAccountMode] = useState<'register' | 'signIn'>('register')
  const [account, setAccount] = useState({ email: '', password: '' })
  const [authError, setAuthError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    document.title = pageTitle('機構申請')
    return () => { document.title = pageTitle() }
  }, [])

  async function run(action: () => Promise<void>) {
    setAuthError(null)
    setBusy(true)
    try {
      await action()
    } catch (caught) {
      if ((caught as { code?: string })?.code === 'auth/email-already-in-use') {
        // 就地切換成登入，不要把人趕去別的頁面重打一次信箱
        setAccountMode('signIn')
        setAuthError('這個信箱已經有帳號了。輸入密碼登入之後就能繼續下一步。')
      } else {
        setAuthError(describeAuthError(caught))
      }
    } finally {
      setBusy(false)
    }
  }

  // ---------------------------------------------------------------- 身分分流

  if (auth.loading) return <Spinner />

  if (auth.email) {
    if (me.isLoading) return <Spinner label="確認身分" />
    if (me.isError) {
      return (
        <Shell>
          <ErrorBanner error={me.error} onRetry={() => void me.refetch()} />
        </Shell>
      )
    }

    const role = effectiveRoleOf(me.data)
    // 已經是機構成員：直接進後台，不必再申請一次
    if (role === 'ORG_MEMBER') return <Navigate to="/org" replace />
    if (role === 'ADMIN') {
      return (
        <Shell>
          <WrongAccountPanel
            expected="ORG_MEMBER"
            reason="平台管理員無法註冊或管理機構，這是為了避免球員兼裁判。"
          />
        </Shell>
      )
    }
    // 帳號已經有了——不管是剛建立的還是本來就登入著，都直接進步驟二
    return <Navigate to="/org/apply" replace />
  }

  const registering = accountMode === 'register'

  return (
    <Shell>
      <StepHeading step={1} title="建立帳號" />

      <p className="text-sm text-slate-600">
        先建立一個帳號，下一步再填機構資料。這與一般民眾用的是同一套帳號系統。
      </p>

      <form
        className="space-y-5"
        onSubmit={(event) => {
          event.preventDefault()
          void run(() => registering
            ? auth.registerWithPassword(account.email, account.password)
            : auth.signInWithPassword(account.email, account.password))
        }}
      >
        {/* 承辦人姓名在下一步跟機構資料一起填——它是機構的欄位，不是帳號的 */}
        <Field label="信箱" required>
          <TextInput required type="email" autoComplete="email"
            placeholder="you@example.com" value={account.email}
            onChange={(e) => {
              setAccount((c) => ({ ...c, email: e.target.value }))
              // signIn 模式只綁定「剛剛被判定為已存在」的那個信箱。換了信箱就切回
              // 註冊，否則使用者改掉打錯的信箱之後，按鈕仍然是「登入」，
              // 會拿到莫名其妙的「信箱或密碼不正確」
              setAccountMode('register')
              setAuthError(null)
            }} />
        </Field>

        {/* 開發模式沒有真的密碼，身分只由 X-Dev-User-Email 標頭指定 */}
        {usingFirebase && (
          <Field label="密碼" required hint={registering ? '至少 6 個字元' : undefined}>
            <TextInput required type="password" minLength={registering ? 6 : undefined}
              autoComplete={registering ? 'new-password' : 'current-password'}
              value={account.password}
              onChange={(e) => setAccount((c) => ({ ...c, password: e.target.value }))} />
          </Field>
        )}

        {authError && (
          <p className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-berry-600" role="alert">
            {authError}
          </p>
        )}

        <Button type="submit" className="w-full py-2.5" disabled={busy}>
          {busy ? '處理中…' : registering ? '建立帳號，前往下一步' : '登入，前往下一步'}
        </Button>
      </form>

      {usingFirebase && (
        <div className="space-y-2 border-t border-slate-100 pt-5">
          <Button variant="secondary" className="w-full py-2.5" disabled={busy}
            onClick={() => void run(() => auth.signIn())}>
            使用 Google 登入
          </Button>
          <p className="text-center text-xs text-slate-500">
            Google 帳號不限 Gmail，任何信箱都能註冊。
            <strong>用 Google 登入不必等驗證信</strong>，可以直接進入下一步。
          </p>
        </div>
      )}

      <p className="text-center text-sm text-slate-500">
        <Link to="/org/login" className="hover:underline">← 回到機構入口</Link>
      </p>
    </Shell>
  )
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
      <div className="w-full max-w-md space-y-5 rounded-xl bg-white p-8 shadow-sm
        ring-1 ring-slate-200">
        {children}
      </div>
    </div>
  )
}
