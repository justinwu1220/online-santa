import { useState } from 'react'
import { describeAuthError, useAuth, usingFirebase } from '../../lib/authContext'
import { Button, Field, TextInput } from '../Form'
import { Notice } from '../Feedback'

type Mode = 'signIn' | 'register' | 'reset'

/**
 * 登入／註冊的共用面板。
 *
 * 三個入口（主網站、機構後台、監控中心）的登入邏輯完全相同，只有標題與說明不同，
 * 所以做成一個元件。分頁切換不改路由——使用者發現自己還沒帳號時不必重新導覽，
 * 帶過來的 `?next=` 也不會遺失。
 *
 * 開發模式沒有真的密碼，只顯示 email 欄位與一個「模擬未驗證」的開關。
 */
export function AuthPanel({ hint, registerHint, allowRegister = true }: {
  /** 登入分頁上方的說明 */
  hint?: string
  /** 註冊分頁上方的說明。沒給就沿用 hint */
  registerHint?: string
  /**
   * 是否提供註冊分頁。監控中心傳 `false`——那個入口只服務已經存在的管理員，
   * 而管理員身分來自白名單，不是註冊來的。擺一個註冊分頁在那裡，等於告訴
   * 誤打誤撞進來的人「這是一個會接受新帳號的登入面」，與這個入口刻意不透露
   * 系統資訊的用意相反。
   */
  allowRegister?: boolean
}) {
  const auth = useAuth()
  const [mode, setMode] = useState<Mode>('signIn')

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [confirmError, setConfirmError] = useState<string | null>(null)
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function switchMode(next: Mode) {
    setMode(next)
    setError(null)
    setNotice(null)
    // 離開註冊分頁時一併清掉——確認密碼欄只在註冊模式出現，殘留的值與錯誤
    // 沒有意義，切回來也該是空白的
    setConfirmPassword('')
    setConfirmError(null)
  }

  async function run(action: () => Promise<void>, successNotice?: string) {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await action()
      if (successNotice) setNotice(successNotice)
    } catch (caught) {
      setError(describeAuthError(caught))
    } finally {
      setBusy(false)
    }
  }

  // ------------------------------------------------------------ 開發模式

  if (!usingFirebase) {
    return (
      <div className="space-y-4">
        <Notice tone="warning">
          開發模式：未設定 Firebase，輸入任何 email 即可登入，沒有任何驗證。
        </Notice>

        <form
          className="space-y-3"
          onSubmit={(event) => {
            event.preventDefault()
            void run(() => auth.signIn(email))
          }}
        >
          <Field label="信箱" required>
            <TextInput type="email" required value={email}
              placeholder="you@example.com"
              onChange={(event) => setEmail(event.target.value)} />
          </Field>

          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input
              type="checkbox"
              checked={auth.devUnverified ?? false}
              onChange={(event) => auth.setDevUnverified?.(event.target.checked)}
            />
            模擬「信箱尚未驗證」——用來檢查認領與機構申請會不會被擋下
          </label>

          <Button type="submit" className="w-full py-2.5" disabled={busy}>
            {busy ? '登入中…' : '登入'}
          </Button>
        </form>
      </div>
    )
  }

  // ------------------------------------------------------------ 忘記密碼

  if (mode === 'reset') {
    return (
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
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
          className="w-full text-sm text-slate-500 hover:underline">
          ← 回到登入
        </button>
      </div>
    )
  }

  // ------------------------------------------------------------ 登入／註冊

  // 一併看 allowRegister：就算 mode 因為任何原因變成 register，
  // 不允許註冊的入口也不會渲染出註冊表單
  const registering = allowRegister && mode === 'register'

  return (
    <div className="space-y-5">
      {allowRegister && (
        <div className="auth-tabs flex rounded-lg bg-slate-100 p-1">
          <Tab active={!registering} onClick={() => switchMode('signIn')}>登入</Tab>
          <Tab active={registering} onClick={() => switchMode('register')}>註冊</Tab>
        </div>
      )}

      {(registering ? registerHint ?? hint : hint) && (
        <p className="text-sm text-slate-600">{registering ? registerHint ?? hint : hint}</p>
      )}

      <form
        className="space-y-3"
        onSubmit={(event) => {
          event.preventDefault()
          if (registering && password !== confirmPassword) {
            setConfirmError('兩次輸入的密碼不一致')
            return
          }
          void run(() => registering
            ? auth.registerWithPassword(email, password, displayName || undefined)
            : auth.signInWithPassword(email, password))
        }}
      >
        {registering && (
          <Field label="顯示名稱" hint="機構聯繫你時會看到這個名字">
            <TextInput maxLength={100} value={displayName}
              placeholder="王小明"
              onChange={(event) => setDisplayName(event.target.value)} />
          </Field>
        )}

        <Field label="信箱" required>
          <TextInput type="email" required autoComplete="email" value={email}
            placeholder="you@example.com"
            onChange={(event) => setEmail(event.target.value)} />
        </Field>

        <Field
          label="密碼"
          required
          hint={registering ? '至少 6 個字元' : undefined}
        >
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

        {registering && (
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

        {error && <ErrorText>{error}</ErrorText>}
        {notice && <Notice tone="success">{notice}</Notice>}

        <Button type="submit" className="w-full py-2.5" disabled={busy}>
          {busy ? '處理中…' : registering ? '註冊' : '登入'}
        </Button>
      </form>

      {registering ? (
        <p className="text-xs text-slate-500">
          註冊後我們會寄一封驗證信。點擊信中的連結之後才能認領願望或申請成為合作機構
          ——機構需要靠這個信箱聯繫你。
        </p>
      ) : (
        <button type="button" onClick={() => switchMode('reset')}
          className="text-sm text-slate-500 hover:underline">
          忘記密碼？
        </button>
      )}

      <div className="flex items-center gap-3">
        <span className="h-px flex-1 bg-slate-200" />
        <span className="text-xs text-slate-400">或</span>
        <span className="h-px flex-1 bg-slate-200" />
      </div>

      <Button
        variant="secondary"
        className="w-full py-2.5"
        disabled={busy}
        onClick={() => void run(() => auth.signIn())}
      >
        使用 Google 登入
      </Button>
      <p className="text-center text-xs text-slate-400">
        Google 帳號不限 Gmail，任何信箱都能註冊
      </p>
    </div>
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
