import { useState } from 'react'
import { describeAuthError, useAuth, usingFirebase } from '../../lib/authContext'
import { Button, Field, TextInput } from '../Form'
import { Notice } from '../Feedback'

type Mode = 'signIn' | 'register' | 'reset'
export type JoinRole = 'donor' | 'org'

/**
 * 登入／註冊面板，全站唯一的登入入口（`/login`）。
 *
 * 三種角色（認領者、機構、管理員）都從這裡登入，登入表單完全相同；差別只在註冊——
 * 先選身分再出現對應欄位。管理員身分來自白名單，不是註冊來的，所以註冊只有兩種。
 *
 * 分頁切換不改路由——使用者發現自己還沒帳號時不必重新導覽，帶過來的 `?next=` 也不會遺失。
 *
 * 開發模式沒有真的密碼，只顯示 email 欄位與一個「模擬未驗證」的開關。
 */
export function AuthPanel({ initialTab = 'signIn', initialRole = null, onJoinIntent }: {
  initialTab?: 'signIn' | 'register'
  initialRole?: JoinRole | null
  /**
   * 送出註冊（或機構註冊途中改走登入）之前通知頁面這個人要加入的身分。
   * 必須在動作之前呼叫：登入一成功 onAuthStateChanged 就會讓頁面重新 render 並導走，
   * 等動作 await 完才通知就來不及決定要導去哪裡。
   */
  onJoinIntent?: (role: JoinRole | null) => void
}) {
  const auth = useAuth()
  const [mode, setMode] = useState<Mode>(initialTab)
  const [role, setRole] = useState<JoinRole | null>(initialRole)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [confirmError, setConfirmError] = useState<string | null>(null)
  const [displayName, setDisplayName] = useState('')
  const [displayNameError, setDisplayNameError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function switchMode(next: Mode) {
    setMode(next)
    setError(null)
    setNotice(null)
    // 確認密碼欄只在註冊模式出現，殘留的值與錯誤沒有意義，切回來也該是空白的
    setConfirmPassword('')
    setConfirmError(null)
    setDisplayNameError(null)
  }

  /** 使用者主動切換分頁：先前的加入意圖作廢（自動切換不走這裡） */
  function userSwitchMode(next: Mode) {
    onJoinIntent?.(null)
    switchMode(next)
  }

  async function run(action: () => Promise<void>, successNotice?: string) {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await action()
      if (successNotice) setNotice(successNotice)
    } catch (caught) {
      if (mode === 'register' && role === 'org'
        && (caught as { code?: string })?.code === 'auth/email-already-in-use') {
        // 就地切成登入，不要把人趕去別的地方重打一次信箱。
        // 身分意圖保留著，登入後仍然會接著走機構申請的下一步
        switchMode('signIn')
        setError('這個信箱已經有帳號了。輸入密碼登入之後就能繼續下一步。')
      } else {
        setError(describeAuthError(caught))
      }
    } finally {
      setBusy(false)
    }
  }

  const registering = mode === 'register'

  // ------------------------------------------------------------ 忘記密碼

  if (usingFirebase && mode === 'reset') {
    return (
      <div className="space-y-4">
        <p className="text-sm text-slate-300">
          輸入註冊時使用的信箱，我們會寄一封重設密碼的信給你。
        </p>

        <form
          className="space-y-3"
          onSubmit={(event) => {
            event.preventDefault()
            void run(
              () => auth.sendPasswordReset(email),
              `如果 ${email} 有註冊過，重設密碼的信已經寄出了。記得檢查垃圾郵件匣。`)
          }}
        >
          <Field label="信箱" required>
            <TextInput type="email" required value={email}
              onChange={(event) => setEmail(event.target.value)} />
          </Field>

          {error && <ErrorText>{error}</ErrorText>}
          {notice && <Notice tone="success">{notice}</Notice>}

          <Button type="submit" className="w-full py-2.5" disabled={busy}>
            {busy ? '寄送中…' : '寄送重設信'}
          </Button>
        </form>

        <button type="button" onClick={() => switchMode('signIn')}
          className="w-full text-sm text-slate-400 hover:text-white hover:underline">
          ← 回到登入
        </button>
      </div>
    )
  }

  // ------------------------------------------------------------ 登入／註冊

  function submit(event: React.FormEvent) {
    event.preventDefault()

    if (!usingFirebase) {
      // 開發模式：身分只由 email 指定，沒有密碼
      onJoinIntent?.(registering ? role : null)
      void run(() => auth.signIn(email))
      return
    }

    if (registering) {
      if (role === 'donor' && !displayName.trim()) {
        setDisplayNameError('請輸入顯示名稱')
        return
      }
      if (password !== confirmPassword) {
        setConfirmError('兩次輸入的密碼不一致')
        return
      }
      onJoinIntent?.(role)
      // 機構的承辦人姓名在下一步跟機構資料一起填——它是機構的欄位，不是帳號的
      void run(() => auth.registerWithPassword(
        email, password, role === 'donor' ? displayName.trim() : undefined))
      return
    }

    // 登入分頁不改寫意圖：從「機構註冊遇到已有帳號」切過來的人要保留它
    void run(() => auth.signInWithPassword(email, password))
  }

  // 註冊分兩頁：先選身分，再填帳號資料——合在一頁在手機上太長
  const chooseRole = registering && role === null
  const showForm = !registering || role !== null

  return (
    <div className="space-y-5">
      {/* 第二頁不放分頁列：版面留給表單，要回頭用下面的「換身分」 */}
      {(!registering || chooseRole) && (
        <div className="auth-tabs flex rounded-lg bg-slate-100 p-1">
          <Tab active={!registering} onClick={() => userSwitchMode('signIn')}>登入</Tab>
          <Tab active={registering} onClick={() => userSwitchMode('register')}>註冊</Tab>
        </div>
      )}

      {chooseRole && (
        <div className="space-y-3">
          <p className="text-sm font-semibold text-white">你想用什麼身分加入？</p>
          <RoleCard
            onClick={() => setRole('donor')}
            icon={<GiftIcon />}
            title="我要認領願望"
            description="挑一個孩子的願望，買禮物寄給機構。一分鐘完成註冊。"
          />
          <RoleCard
            onClick={() => setRole('org')}
            icon={<HomeIcon />}
            title="我是社福機構或學校"
            description="刊登服務對象的願望、管理收件。需要文件審核。"
          />
          <p className="pt-1 text-center text-xs text-slate-400">
            選好身分之後，下一步再填信箱與密碼。
          </p>
        </div>
      )}

      {registering && role !== null && (
        <h2 className="flex items-center gap-3 text-base font-semibold text-white">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg
            bg-white/10 text-emerald-300">
            {role === 'org' ? <HomeIcon /> : <GiftIcon />}
          </span>
          {role === 'org' ? '我是社福機構或學校' : '我要認領願望'}
        </h2>
      )}

      {showForm && (
        <form className="space-y-3" onSubmit={submit}>
          {!usingFirebase && (
            <Notice tone="warning">
              開發模式：未設定 Firebase，輸入任何 email 即可登入，沒有任何驗證。
            </Notice>
          )}

          {usingFirebase && registering && role === 'donor' && (
            <Field label="顯示名稱" required hint="機構聯繫你時會看到這個名字"
              error={displayNameError ?? undefined}>
              <TextInput required maxLength={100} value={displayName}
                placeholder="王小明"
                onChange={(event) => {
                  setDisplayName(event.target.value)
                  setDisplayNameError(null)
                }} />
            </Field>
          )}

          <Field label="信箱" required>
            <TextInput type="email" required autoComplete="email" value={email}
              placeholder="you@example.com"
              onChange={(event) => setEmail(event.target.value)} />
          </Field>

          {usingFirebase && (
            <Field label="密碼" required hint={registering ? '至少 6 個字元' : undefined}>
              <TextInput
                type="password"
                required
                minLength={registering ? 6 : undefined}
                autoComplete={registering ? 'new-password' : 'current-password'}
                value={password}
                onChange={(event) => {
                  setPassword(event.target.value)
                  setConfirmError(null)
                }}
              />
            </Field>
          )}

          {usingFirebase && registering && (
            <Field label="確認密碼" required error={confirmError ?? undefined}>
              <TextInput
                type="password"
                required
                minLength={6}
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(event) => {
                  setConfirmPassword(event.target.value)
                  setConfirmError(null)
                }}
              />
            </Field>
          )}

          {!usingFirebase && (
            <label className="flex items-center gap-2 text-sm text-slate-300">
              <input
                type="checkbox"
                checked={auth.devUnverified ?? false}
                onChange={(event) => auth.setDevUnverified?.(event.target.checked)}
              />
              模擬「信箱尚未驗證」——用來檢查認領與機構申請會不會被擋下
            </label>
          )}

          {error && <ErrorText>{error}</ErrorText>}
          {notice && <Notice tone="success">{notice}</Notice>}

          <Button type="submit" className="w-full py-2.5" disabled={busy}>
            {busy ? '處理中…'
              : !registering ? '登入'
                : role === 'org' ? '建立帳號，前往下一步' : '註冊'}
          </Button>

          {usingFirebase && registering && (
            <p className="text-xs text-slate-400">
              註冊後我們會寄一封驗證信。點擊信中的連結之後才能認領願望或申請成為合作機構
              ——機構需要靠這個信箱聯繫你。
            </p>
          )}
        </form>
      )}

      {registering && role !== null && (
        <button type="button" onClick={() => setRole(null)}
          className="w-full text-sm text-slate-400 hover:text-white hover:underline">
          ← 上一頁
        </button>
      )}

      {usingFirebase && !registering && (
        <button type="button" onClick={() => switchMode('reset')}
          className="text-sm text-slate-400 hover:text-white hover:underline">
          忘記密碼？
        </button>
      )}

      {chooseRole && (
        <p className="text-center text-sm text-slate-300">
          已經有帳號了？{' '}
          <button type="button" onClick={() => userSwitchMode('signIn')}
            className="font-medium text-emerald-300 hover:underline">
            登入
          </button>
        </p>
      )}
    </div>
  )
}

function RoleCard({ onClick, icon, title, description }: {
  onClick: () => void
  icon: React.ReactNode
  title: string
  description: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center gap-4 rounded-2xl border border-white/10
        bg-white/5 px-4 py-4 text-left transition-colors hover:border-emerald-400/40
        hover:bg-white/10"
    >
      <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl
        bg-white/10 text-emerald-300">
        {icon}
      </span>
      <span className="min-w-0">
        <span className="block font-semibold text-white">{title}</span>
        <span className="mt-1 block text-sm text-slate-300">{description}</span>
      </span>
    </button>
  )
}

function GiftIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round"
      className="h-6 w-6" aria-hidden>
      <rect x="3" y="8" width="18" height="4" rx="1" />
      <path d="M12 8v13M5 12v8a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-8" />
      <path d="M7.5 8a2.5 2.5 0 0 1 0-5C10 3 12 8 12 8s2-5 4.5-5a2.5 2.5 0 0 1 0 5" />
    </svg>
  )
}

function HomeIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round"
      className="h-6 w-6" aria-hidden>
      <path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z" />
      <path d="M9.5 21v-6h5v6" />
    </svg>
  )
}

function Tab({ active, onClick, children }: {
  active: boolean; onClick: () => void; children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`auth-tab flex-1 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
        active ? 'auth-tab-active bg-white text-slate-900 shadow-sm'
          : 'text-slate-500 hover:text-slate-700'
      }`}
    >
      {children}
    </button>
  )
}

function ErrorText({ children }: { children: React.ReactNode }) {
  return (
    <p className="form-error rounded-lg bg-rose-50 px-3 py-2 text-sm text-berry-600" role="alert">
      {children}
    </p>
  )
}
