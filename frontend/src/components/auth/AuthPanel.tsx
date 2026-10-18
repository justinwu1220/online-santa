import { ArrowLeft, ChevronRight, Eye, EyeOff, Gift, Heart, House } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { describeAuthError, useAuth, usingFirebase } from '../../lib/authContext'
import { termsFlow } from '../../lib/terms'
import { SentScreen } from './SentScreen'
import { TermsDialog } from '../terms/TermsDialog'
import { DONOR_TERMS } from '../terms/termsContent'
import { FormField, Input } from '../ui/Fields'
import { useSlideSwap } from '../ui/SlideSwap'

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
export function AuthPanel({ initialTab = 'signIn', initialRole = null, onJoinIntent, onAwaitVerification }: {
  initialTab?: 'signIn' | 'register'
  initialRole?: JoinRole | null
  /**
   * 送出註冊（或機構註冊途中改走登入）之前通知頁面這個人要加入的身分。
   * 必須在動作之前呼叫：登入一成功 onAuthStateChanged 就會讓頁面重新 render 並導走，
   * 等動作 await 完才通知就來不及決定要導去哪裡。
   */
  onJoinIntent?: (role: JoinRole | null) => void
  /**
   * 認領者註冊成功後要停在「驗證信寄出了」畫面，頁面不能因為已登入就導走。
   * 同樣必須在建立帳號<strong>之前</strong>通知（帳號一建好登入狀態就變了）；註冊失敗再通知 false。
   * 驗證完成後通知 false，頁面才會導向原本要去的地方。
   */
  onAwaitVerification?: (waiting: boolean) => void
}) {
  const auth = useAuth()
  const queryClient = useQueryClient()
  // 認領者按「註冊」後先跳出須知；同意後才真正建立帳號（SPEC 13.1）
  const [termsOpen, setTermsOpen] = useState(false)
  // 註冊成功後的「驗證信寄出了」畫面
  const [sent, setSent] = useState(false)
  const [mode, setMode] = useState<Mode>(initialTab)
  const [role, setRole] = useState<JoinRole | null>(initialRole)
  // 膠囊滑塊跟著手勢先動，所以和實際換內容的 mode 分開記
  const [seg, setSeg] = useState<'signIn' | 'register'>(initialTab === 'register' ? 'register' : 'signIn')
  const { body, swap } = useSlideSwap()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [confirmPassword, setConfirmPassword] = useState('')
  const [confirmError, setConfirmError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [displayName, setDisplayName] = useState('')
  const [displayNameError, setDisplayNameError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function switchMode(next: Mode, message: string | null = null) {
    const dir = next === 'signIn' ? -1 : 1
    void swap(dir, () => applyMode(next, message),
      () => { if (next !== 'reset') setSeg(next) })
  }

  /** 換到另一個身分選擇／表單步驟（往下一步向左滑、回上一步向右滑） */
  function changeRole(next: JoinRole | null) {
    void swap(next === null ? -1 : 1, () => setRole(next))
  }

  function applyMode(next: Mode, message: string | null) {
    setMode(next)
    setError(message)
    setNotice(null)
    // 確認密碼欄只在註冊模式出現，殘留的值與錯誤沒有意義，切回來也該是空白的
    setConfirmPassword('')
    setConfirmError(null)
    setDisplayNameError(null)
    setNameError(null)
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
        switchMode('signIn', '這個信箱已經有帳號了。輸入密碼登入之後就能繼續下一步。')
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
      <div ref={body} className="auth-body">
        <div className="screen-head">
          <h2>重設密碼</h2>
        </div>
        <form
          className="auth-form"
          onSubmit={(event) => {
            event.preventDefault()
            void run(
              () => auth.sendPasswordReset(email),
              `如果 ${email} 有註冊過，重設密碼的信已經寄出了。記得檢查垃圾郵件匣。`)
          }}
        >
          <p className="auth-note">輸入註冊時使用的信箱，我們會寄一封重設密碼的信給你。</p>
          <FormField label="信箱" required>
            <Input type="email" required value={email}
              onChange={(event) => setEmail(event.target.value)} />
          </FormField>

          {error && <p className="auth-msg auth-msg--danger" role="alert">{error}</p>}
          {notice && <p className="auth-msg auth-msg--ok">{notice}</p>}

          <button type="submit" className="btn btn--primary" disabled={busy}>
            {busy ? '寄送中…' : '寄送重設信'}
          </button>
          <button type="button" className="back-link" onClick={() => switchMode('signIn')}>
            <ArrowLeft className="icon" aria-hidden />回到登入
          </button>
        </form>
      </div>
    )
  }

  // ------------------------------------------------------------ 驗證信已寄出

  if (sent) {
    return (
      <div ref={body} className="auth-body">
        <SentScreen onVerified={() => onAwaitVerification?.(false)} />
      </div>
    )
  }

  // ------------------------------------------------------------ 登入／註冊

  function submit(event: React.FormEvent) {
    event.preventDefault()

    if (!usingFirebase && !registering) {
      // 開發模式登入：身分只由 email 指定，沒有密碼
      onJoinIntent?.(null)
      void run(() => auth.signIn(email))
      return
    }

    if (registering) {
      if (role === 'donor' && !name.trim()) {
        setNameError('請輸入真實姓名，不想具名請按「使用善心人士名義」')
        return
      }
      if (role === 'donor' && !displayName.trim()) {
        setDisplayNameError('請輸入顯示名稱')
        return
      }
      if (password !== confirmPassword) {
        setConfirmError('兩次輸入的密碼不一致')
        return
      }
      if (role === 'donor') {
        // 先跳出《認領者參與須知》，取消就回到這張表單（欄位值都還在）
        setTermsOpen(true)
        return
      }
      onJoinIntent?.(role)
      // 機構的承辦人姓名在下一步跟機構資料一起填——它是機構的欄位，不是帳號的
      void run(() => auth.registerWithPassword(email, password))
      return
    }

    // 登入分頁不改寫意圖：從「機構註冊遇到已有帳號」切過來的人要保留它
    void run(() => auth.signInWithPassword(email, password))
  }

  /** 認領者在須知上按「同意並註冊」：關掉視窗，建立帳號並寫入同意紀錄。 */
  function registerDonorAfterConsent() {
    setTermsOpen(false)
    onJoinIntent?.('donor')
    onAwaitVerification?.(true)
    void run(async () => {
      // 帳號一建好就會登入，但同意紀錄還在送：這段時間守門（TermsGate）不要跳出第二個視窗
      termsFlow.registering = true
      try {
        await auth.registerWithPassword(
          email, password, displayName.trim(), name.trim(), DONOR_TERMS.version)
      } catch (caught) {
        onAwaitVerification?.(false)
        throw caught
      } finally {
        termsFlow.registering = false
        void queryClient.invalidateQueries({ queryKey: ['terms'] })
      }
      // 帳號建好了：同一張卡片滑到「驗證信寄出了」
      await swap(1, () => setSent(true))
    })
  }

  // 註冊分兩頁：先選身分，再填帳號資料——合在一頁在手機上太長
  const chooseRole = registering && role === null
  const showForm = !registering || role !== null

  return (
    <div ref={body} className="auth-body">
      {/* 第二頁不放分頁列：版面留給表單，要回頭用「上一步」 */}
      {(!registering || chooseRole) && (
        <div className="seg" role="tablist" data-active={seg}>
          <span className="seg__thumb" aria-hidden />
          <button type="button" role="tab" aria-selected={!registering}
            onClick={() => userSwitchMode('signIn')}>登入</button>
          <button type="button" role="tab" aria-selected={registering}
            onClick={() => userSwitchMode('register')}>註冊</button>
        </div>
      )}

      {chooseRole && (
        <>
          <h2 className="role-title">你想用什麼身分加入？</h2>
          <div className="role">
            <button type="button" className="role-card" onClick={() => changeRole('donor')}>
              <span className="role-card__icon"><Gift className="icon" aria-hidden /></span>
              <span>
                <strong>我要認領願望</strong>
                <span className="role-card__desc">挑一個孩子的願望，買禮物寄給機構。一分鐘完成註冊。</span>
              </span>
              <ChevronRight className="role-card__go icon" aria-hidden />
            </button>
            <button type="button" className="role-card role-card--org" onClick={() => changeRole('org')}>
              <span className="role-card__icon"><House className="icon" aria-hidden /></span>
              <span>
                <strong>我是社福機構或學校</strong>
                <span className="role-card__desc">刊登服務對象的願望、管理收件。需要文件審核。</span>
              </span>
              <ChevronRight className="role-card__go icon" aria-hidden />
            </button>
          </div>
          <p className="auth-note auth-note--center">選好身分之後，下一步再填信箱與密碼。</p>
          <p className="auth-foot">
            已經有帳號了？{' '}
            <button type="button" onClick={() => userSwitchMode('signIn')}>登入</button>
          </p>
        </>
      )}

      {registering && role !== null && (
        <div className="screen-head">
          <span className={`screen-head__icon${role === 'org' ? ' screen-head__icon--org' : ''}`}>
            {role === 'org' ? <House className="icon" aria-hidden /> : <Gift className="icon" aria-hidden />}
          </span>
          <h2>
            {role === 'org' && <span className="screen-head__step">步驟 1 / 2　建立帳號</span>}
            {role === 'org' ? '我是社福機構或學校' : '我要認領願望'}
          </h2>
        </div>
      )}

      {showForm && (
        <form className="auth-form" onSubmit={submit}>
          {!usingFirebase && (
            <p className="auth-msg">
              開發模式：未設定 Firebase，輸入任何 email 即可登入，沒有任何驗證。註冊時密碼只用來檢查畫面、不會驗證，姓名則會儲存。
            </p>
          )}

          {registering && role === 'donor' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <FormField label="真實姓名" required
                hint="用於捐贈收據與向衛福部申報的徵信名冊，不會顯示在網站上"
                error={nameError}>
                <Input required maxLength={100} autoComplete="name" value={name}
                  placeholder="王大明"
                  onChange={(event) => {
                    setName(event.target.value)
                    setNameError(null)
                  }} />
              </FormField>
              <button type="button" className="btn btn--sm kind-btn"
                onClick={() => { setName('善心人士'); setNameError(null) }}>
                <Heart className="icon" aria-hidden />
                使用善心人士名義
              </button>
            </div>
          )}

          {registering && role === 'donor' && (
            <FormField label="顯示名稱" required hint="機構和孩子看到的名字，可以用暱稱"
              error={displayNameError}>
              <Input required maxLength={100} value={displayName} placeholder="王小明"
                onChange={(event) => {
                  setDisplayName(event.target.value)
                  setDisplayNameError(null)
                }} />
            </FormField>
          )}

          <FormField label="信箱" required>
            <Input type="email" required autoComplete="email" value={email}
              placeholder="you@example.com"
              onChange={(event) => setEmail(event.target.value)} />
          </FormField>

          {(usingFirebase || registering) && (
            <FormField label="密碼" required hint={registering ? '至少 6 個字元' : undefined}>
              <span className="pw-wrap">
                <Input
                  type={showPassword ? 'text' : 'password'}
                  required
                  minLength={registering ? 6 : undefined}
                  autoComplete={registering ? 'new-password' : 'current-password'}
                  value={password}
                  onChange={(event) => {
                    setPassword(event.target.value)
                    setConfirmError(null)
                  }}
                />
                <button type="button" className="pw-toggle" onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? '隱藏密碼' : '顯示密碼'}>
                  {showPassword ? <EyeOff className="icon" aria-hidden /> : <Eye className="icon" aria-hidden />}
                </button>
              </span>
            </FormField>
          )}

          {registering && (
            <FormField label="確認密碼" required error={confirmError}>
              <Input
                type={showPassword ? 'text' : 'password'}
                required
                minLength={6}
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(event) => {
                  setConfirmPassword(event.target.value)
                  setConfirmError(null)
                }}
              />
            </FormField>
          )}

          {!usingFirebase && (
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: 'var(--fg-4)' }}>
              <input
                type="checkbox"
                checked={auth.devUnverified ?? false}
                onChange={(event) => auth.setDevUnverified?.(event.target.checked)}
              />
              模擬「信箱尚未驗證」——用來檢查認領與機構申請會不會被擋下
            </label>
          )}

          {error && <p className="auth-msg auth-msg--danger" role="alert">{error}</p>}
          {notice && <p className="auth-msg auth-msg--ok">{notice}</p>}

          <button type="submit" className="btn btn--primary" disabled={busy}>
            {busy ? '處理中…'
              : !registering ? '登入'
                : role === 'org' ? '建立帳號，前往下一步' : '註冊'}
          </button>

          {registering && role !== null && (
            <p className="auth-note" style={{ textAlign: 'center' }}>
              {role === 'org'
                ? '註冊後我們會寄一封驗證信。完成驗證後才能送出機構申請——機構需要靠這個信箱聯繫你。'
                : '註冊後我們會寄一封驗證信。點信中的連結之後，才能認領願望。'}
            </p>
          )}

          {registering && role !== null && (
            <button type="button" className="back-link" onClick={() => changeRole(null)}>
              <ArrowLeft className="icon" aria-hidden />上一步
            </button>
          )}

          {usingFirebase && !registering && (
            <button type="button" className="auth-link auth-link--left" onClick={() => switchMode('reset')}>
              忘記密碼？
            </button>
          )}
        </form>
      )}

      <TermsDialog
        open={termsOpen}
        onClose={() => setTermsOpen(false)}
        terms={DONOR_TERMS}
        mode="consent"
        acceptLabel="同意並註冊"
        onAccept={registerDonorAfterConsent}
      />
    </div>
  )
}
