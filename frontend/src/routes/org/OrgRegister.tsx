import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { ApiError, api } from '../../lib/api'
import { describeAuthError, useAuth, usingFirebase } from '../../lib/authContext'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import type { OrganizationView } from '../../lib/types'
import { EmailVerificationBanner } from '../../components/EmailVerificationBanner'
import { WrongAccountPanel } from '../../components/auth/WrongAccountPanel'
import { ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { Button, Field, TextArea, TextInput } from '../../components/Form'

/**
 * 機構自助註冊——一頁到底。
 *
 * 這一頁對未登入者也開放：新機構從主網站頁尾點進來時還沒有任何帳號，先把人丟到一個
 * 通用的登入面板、再導來這裡填第二張表單，會讓人以為自己註冊了兩次。所以帳號欄位與
 * 機構欄位放在同一張表單上，資料只輸入一次。
 *
 * 授權不靠這一頁：POST /api/organizations 自己會擋下未驗證的信箱、已隸屬機構的人與
 * 管理員。這裡的角色分流只是為了不要讓人白填一張表單。
 */
export function OrgRegister() {
  const auth = useAuth()
  const me = useCurrentUser()
  const queryClient = useQueryClient()

  const signedIn = Boolean(auth.email)

  // ---------------------------------------------------------------- 帳號區塊
  const [accountMode, setAccountMode] = useState<'register' | 'signIn'>('register')
  const [account, setAccount] = useState({ contactName: '', email: '', password: '' })
  const [authError, setAuthError] = useState<string | null>(null)
  const [authBusy, setAuthBusy] = useState(false)
  // 帳號是在這一頁剛建立/登入的。用來決定要不要提示「還有第二步」——
  // 從機構入口帶著身分進來的人不需要那句話，表單本身就夠清楚了
  const [accountJustReady, setAccountJustReady] = useState(false)

  // ---------------------------------------------------------------- 機構區塊
  const [form, setForm] = useState({
    name: '', contactEmail: '', contactPhone: '', address: '', description: '',
  })
  // 聯絡信箱預設跟著帳號信箱走，使用者改過之後就不再覆寫——多數機構的聯絡信箱就是
  // 登入信箱，而這是整條流程裡唯一字面上真正重複的欄位
  const [contactEmailTouched, setContactEmailTouched] = useState(false)
  const contactEmail = contactEmailTouched ? form.contactEmail : (auth.email ?? account.email)

  const register = useMutation({
    mutationFn: () => api.post<OrganizationView>('/api/organizations', { ...form, contactEmail }),
    onSuccess: () => {
      // 註冊者會從 DONOR 變成 ORG_MEMBER，身分要重新載入。
      //
      // 這裡刻意不自己 navigate('/org')：身分重新載入是非同步的，馬上導過去的話
      // /org 的 RequireRole 讀到的還是舊的 DONOR，會把人彈回登入頁再彈回來。
      // 下面的角色分流看到 ORG_MEMBER 就會自動導向，等身分真的更新了才走。
      void queryClient.invalidateQueries({ queryKey: ['me'] })
      void queryClient.invalidateQueries({ queryKey: ['organization'] })
    },
  })

  const update = (key: keyof typeof form) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => setForm((current) => ({ ...current, [key]: event.target.value }))

  const fieldErrors = register.error instanceof ApiError ? register.error.fieldErrors : undefined

  /**
   * 建立帳號與送出申請刻意分成兩次按下，不串在同一個 await 鏈裡。
   *
   * api 模組的驗證標頭是 AuthHeaderBridge 在 useEffect 裡登記的，要等
   * onAuthStateChanged 觸發重新 render 之後才會更新。剛建立完帳號就接著打 API，
   * 送出的可能是還沒帶 token 的舊標頭。而且密碼註冊本來就必須停下來等使用者點
   * 驗證信，串起來也省不了那一次點擊。
   */
  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setAuthError(null)

    if (!signedIn) {
      setAuthBusy(true)
      try {
        if (accountMode === 'register') {
          await auth.registerWithPassword(
            account.email, account.password, account.contactName || undefined)
        } else {
          await auth.signInWithPassword(account.email, account.password)
        }
        setAccountJustReady(true)
      } catch (caught) {
        if ((caught as { code?: string })?.code === 'auth/email-already-in-use') {
          // 不要把人導去登入頁——已經填好的機構資料會全部消失
          setAccountMode('signIn')
          setAuthError('這個信箱已經有帳號了。輸入密碼登入之後就能繼續，你填的機構資料會留著。')
        } else {
          setAuthError(describeAuthError(caught))
        }
      } finally {
        setAuthBusy(false)
      }
      return
    }

    if (!auth.emailVerified) return
    register.mutate()
  }

  async function signInWithGoogle() {
    setAuthError(null)
    setAuthBusy(true)
    try {
      await auth.signIn()
      setAccountJustReady(true)
    } catch (caught) {
      setAuthError(describeAuthError(caught))
    } finally {
      setAuthBusy(false)
    }
  }

  // ---------------------------------------------------------------- 身分分流

  if (auth.loading) return <Spinner />

  if (signedIn) {
    if (me.isLoading) return <Spinner label="確認身分" />
    if (me.isError) {
      return (
        <Shell>
          <ErrorBanner error={me.error} onRetry={() => void me.refetch()} />
        </Shell>
      )
    }

    const role = effectiveRoleOf(me.data)
    // 已經是機構成員：直接進後台，不必再看到申請表單
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
  }

  // 申請已送出，但身分還在重新載入。不留在表單上——使用者會以為沒送出去而再按一次
  if (register.isSuccess) {
    return <Shell><Spinner label="申請已送出，正在進入機構後台" /></Shell>
  }

  const registering = accountMode === 'register'
  const busy = authBusy || register.isPending
  // 「並繼續」不是贅字：這一步不會送出申請，標籤要先講清楚，
  // 否則使用者按完看不到變化，會以為送出失敗
  const submitLabel = signedIn ? '送出申請' : (registering ? '建立帳號並繼續' : '登入並繼續')

  return (
    <>
      {/*
        這一頁不在任何 layout 底下（App.tsx 的 org/register 是獨立路由），橫幅要自己帶。
        少了它，未驗證的人會看到停用的送出鈕卻沒有任何重寄或更新狀態的入口——而且
        已經點過驗證信的人也會卡住，因為更新 token 的按鈕正是橫幅上那一顆。
      */}
      <EmailVerificationBanner />

      <Shell>
        <div>
          <h1 className="text-3xl font-bold text-santa-700">機構註冊</h1>
          <p className="mt-2 text-slate-600">
            填一次資料就能送出申請。平台審核通過後即可上架孩子的願望。
          </p>
        </div>

        <Notice tone="warning">
          成為機構成員後，<strong>這個帳號將無法再以個人身分認領願望</strong>——
          一個帳號只能有一種身分。如果你也想以個人身分參與，建議機構改用另一個
          聯絡信箱註冊。
        </Notice>

        <form className="space-y-8" onSubmit={handleSubmit}>
          {/* ---------------------------------------------------------- 帳號 */}
          <fieldset className="space-y-5">
            <legend className="text-sm font-semibold text-slate-800">帳號</legend>

            {signedIn ? (
              <SignedInRow email={auth.email ?? ''} onSignOut={() => void auth.signOut()} />
            ) : (
              <>
                {registering && (
                  <Field label="承辦人姓名"
                    hint="填表這個人的姓名，不是機構名稱——機構名稱在下一段填">
                    <TextInput maxLength={100} placeholder="王小明"
                      value={account.contactName}
                      onChange={(e) => setAccount((c) => ({ ...c, contactName: e.target.value }))} />
                  </Field>
                )}

                <Field label="信箱" required>
                  <TextInput required type="email" autoComplete="email"
                    placeholder="you@example.com" value={account.email}
                    onChange={(e) => {
                      setAccount((c) => ({ ...c, email: e.target.value }))
                      // signIn 模式只綁定「剛剛被判定為已存在」的那個信箱。
                      // 換了信箱就回到註冊，否則使用者改掉打錯的信箱之後，
                      // 按鈕仍然是「登入」，會拿到莫名其妙的「信箱或密碼不正確」
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

                {usingFirebase && (
                  <div className="space-y-2 border-t border-slate-100 pt-5">
                    <Button variant="secondary" className="w-full py-2.5" disabled={busy}
                      onClick={() => void signInWithGoogle()}>
                      使用 Google 登入
                    </Button>
                    <p className="text-center text-xs text-slate-500">
                      Google 帳號不限 Gmail，任何信箱都能註冊。
                      <strong>用 Google 登入不必等驗證信</strong>，可以直接送出申請。
                    </p>
                  </div>
                )}
              </>
            )}
          </fieldset>

          {/* ---------------------------------------------------------- 機構 */}
          <fieldset className="space-y-5">
            <legend className="text-sm font-semibold text-slate-800">機構資料</legend>

            {/*
              required 只在真的要送出機構申請時才生效。還沒登入時這顆按鈕做的是
              「建立帳號」或「登入」——回頭的機構成員切到登入模式卻被空白的機構
              名稱擋住，會完全不知道發生什麼事。星號照常顯示，欄位終究是必填的。
            */}
            <Field label="機構名稱" required error={fieldErrors?.name}>
              <TextInput required={signedIn} maxLength={120} placeholder="某某社會福利基金會"
                value={form.name} onChange={update('name')} />
            </Field>
            <Field label="聯絡信箱" required error={fieldErrors?.contactEmail}
              hint={contactEmailTouched ? undefined : '預設與帳號信箱相同，可以改'}>
              <TextInput required={signedIn} type="email" maxLength={255} value={contactEmail}
                onChange={(event) => {
                  setContactEmailTouched(true)
                  update('contactEmail')(event)
                }} />
            </Field>
            <div className="grid gap-5 sm:grid-cols-2">
              <Field label="聯絡電話">
                <TextInput maxLength={40} value={form.contactPhone} onChange={update('contactPhone')} />
              </Field>
              <Field label="地址">
                <TextInput maxLength={255} value={form.address} onChange={update('address')} />
              </Field>
            </div>
            <Field label="機構簡介" hint="讓捐贈者了解你們服務的對象">
              <TextArea rows={4} maxLength={2000}
                value={form.description} onChange={update('description')} />
            </Field>
          </fieldset>

          {signedIn && auth.emailVerified && accountJustReady && (
            <Notice tone="success">
              <p className="font-medium">帳號完成，還沒送出申請</p>
              <p className="mt-1">
                機構資料還在下面這張表單裡。確認無誤後按「<strong>送出申請</strong>」才會
                真的提出申請。
              </p>
            </Notice>
          )}

          {signedIn && !auth.emailVerified && (
            <Notice tone="warning">
              <p className="font-medium">帳號已建立，還差信箱驗證</p>
              <p className="mt-1">
                機構申請通過後就能上架孩童資料，門檻不能只是「填了一個信箱」。
                驗證信寄到 <strong>{auth.email}</strong>，點完連結後回到這一頁，
                按上方橫幅的「我已經驗證好了」就能送出。
                <strong>你填的資料會留在畫面上。</strong>
              </p>
            </Notice>
          )}

          {register.isError && <ErrorBanner error={register.error} />}

          <Button type="submit" disabled={busy || (signedIn && !auth.emailVerified)}>
            {busy ? '處理中…' : submitLabel}
          </Button>
        </form>

        {!signedIn && (
          <p className="text-center text-sm text-slate-500">
            <Link to="/org/login" className="hover:underline">← 回到機構入口</Link>
          </p>
        )}
      </Shell>
    </>
  )
}

function Shell({ children }: { children: React.ReactNode }) {
  return <section className="mx-auto max-w-2xl space-y-6 px-4 py-10">{children}</section>
}

function SignedInRow({ email, onSignOut }: { email: string; onSignOut: () => void }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg
      bg-slate-50 px-3 py-2.5 text-sm">
      <span className="text-slate-600">
        已登入為 <strong className="text-slate-800">{email}</strong>
      </span>
      <button type="button" onClick={onSignOut}
        className="text-xs text-slate-500 hover:underline">
        改用其他帳號
      </button>
    </div>
  )
}
