import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../lib/api'
import { describeAuthError, useAuth, usingFirebase } from '../lib/authContext'
import { pageTitle } from '../lib/brand'
import { formatDateTime } from '../lib/format'
import { useDonorTerms } from '../lib/terms'
import { useCurrentUser } from '../lib/useCurrentUser'
import { useMyProfile } from '../lib/useMyProfile'
import { useResendVerification } from '../lib/useResendVerification'
import type { CurrentUser, UserProfile, UserRole } from '../lib/types'
import { KIND_NAME, KindNameButton } from '../components/auth/KindNameButton'
import { useOnboard } from '../components/onboard/onboardContext'
import { DonorTermsLink } from '../components/terms/DonorTermsLink'
import { DONOR_TERMS } from '../components/terms/termsContent'
import { FormField, Input } from '../components/ui/Fields'
import { ErrorNotice, Spinner } from '../components/ui/Spinner'

const ROLE_LABEL: Record<UserRole, string> = {
  DONOR: '認領者',
  ORG_MEMBER: '合作機構',
  ADMIN: '管理員',
}

/**
 * 個人資料頁。
 *
 * 原型沒有這一頁，版面沿用原型的元件語彙（list-head、panel、field、badge），欄位名稱與提示
 * 與註冊表單一致。用戶名稱會在認領後以 donorName 顯示給機構（見 ClaimOrgView / AdminClaimView），
 * 欄位提示要讓使用者先知道這件事，而不是事後才發現。
 */
export function MyProfile() {
  const { email } = useAuth()
  const profile = useMyProfile()
  const user = useCurrentUser()

  useEffect(() => {
    document.title = pageTitle('個人資料')
    return () => { document.title = pageTitle() }
  }, [])

  if (!email) {
    return <div className="notice notice--info" style={{ marginTop: 40 }}>請先在右上角登入，才能查看個人資料。</div>
  }

  return (
    <section style={{ maxWidth: 720 }}>
      <div className="list-head">
        <h1>個人資料</h1>
        <p>這些資料用於認領、寄送聯繫與捐贈收據。</p>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
        {profile.isLoading && <Spinner label="載入個人資料" />}
        {profile.isError && <ErrorNotice error={profile.error} onRetry={() => void profile.refetch()} />}

        {profile.data && (
          <>
            <AccountSection profile={profile.data} user={user.data} />
            <ProfileForm profile={profile.data} isDonor={user.data?.role === 'DONOR'} />
            {user.data?.role === 'DONOR' && <TermsSection />}
            <SecuritySection email={email} />
          </>
        )}
      </div>
    </section>
  )
}

/** ① 帳號：信箱、驗證狀態、身分。 */
function AccountSection({ profile, user }: { profile: UserProfile; user?: CurrentUser }) {
  const resend = useResendVerification()

  return (
    <section className="panel">
      <div className="panel__head"><h2 className="panel__title">帳號</h2></div>

      <dl className="ship-summary">
        <div>
          <dt>信箱</dt>
          <dd>
            {profile.email}{' '}
            <span className={`badge ${profile.emailVerified ? 'badge--ok' : 'badge--warn'}`}>
              {profile.emailVerified ? '已驗證' : '未驗證'}
            </span>
          </dd>
        </div>
        <div>
          <dt>身分</dt>
          <dd>
            {user ? ROLE_LABEL[user.role] : '—'}
            {user?.role === 'ORG_MEMBER' && (
              <>{' '}<Link to="/org/settings" className="terms-link">前往機構設定</Link></>
            )}
          </dd>
        </div>
      </dl>

      {!profile.emailVerified && (
        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div>
            <button type="button" className="btn" disabled={resend.busy || resend.remaining > 0}
              onClick={() => void resend.resend()}>
              {resend.remaining > 0 ? `重新寄送驗證信（${resend.remaining}）` : '重新寄送驗證信'}
            </button>
          </div>
          {resend.message && <p className="auth-msg auth-msg--ok" style={{ margin: 0 }}>
            {resend.message}，請查看信箱（記得檢查垃圾郵件匣）。
          </p>}
          {resend.error && <p className="auth-msg auth-msg--danger" style={{ margin: 0 }}>{resend.error}</p>}
        </div>
      )}
    </section>
  )
}

/** ② 基本資料：真實姓名、顯示名稱、連絡電話。標籤與提示和註冊表單一致。 */
function ProfileForm({ profile, isDonor }: { profile: UserProfile; isDonor: boolean }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    displayName: profile.displayName,
    name: profile.name ?? '',
    phone: profile.phone ?? '',
  })

  const save = useMutation({
    mutationFn: () => api.patch<UserProfile>('/api/me/profile', form),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['me', 'profile'] })
      void queryClient.invalidateQueries({ queryKey: ['me'] })
    },
  })

  /**
   * 清掉上一次儲存的結果。
   *
   * 表單一有變動就要清——否則使用者改完東西還看得到綠色的「已儲存」，
   * 會以為新的改動也存進去了。錯誤訊息同理：它描述的是舊的那一次送出。
   */
  const clearSaveResult = () => {
    if (save.isSuccess || save.isError) save.reset()
  }

  const update = (key: keyof typeof form) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setForm((current) => ({ ...current, [key]: event.target.value }))
    clearSaveResult()
  }

  const fieldErrors = save.error instanceof ApiError ? save.error.fieldErrors : undefined
  // 後端欄位錯誤顯示在欄位下方；只有對應不到欄位的錯誤才用通用提示框
  const onlyFieldErrors = Boolean(fieldErrors)
    && Object.keys(fieldErrors!).every((key) => ['name', 'displayName', 'phone'].includes(key))

  return (
    <form className="panel" onSubmit={(event) => { event.preventDefault(); save.mutate() }}>
      <div className="panel__head"><h2 className="panel__title">基本資料</h2></div>

      <div className="form-grid">
        <div className="c-6" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <FormField label="真實姓名" required={isDonor} optional={!isDonor} error={fieldErrors?.name}
            hint="用於捐贈收據與向衛福部申報的徵信名冊，不會顯示在網站上">
            <Input required={isDonor} maxLength={100} autoComplete="name" value={form.name}
              placeholder="王大明" onChange={update('name')} />
          </FormField>
          <KindNameButton onPick={() => {
            setForm((current) => ({ ...current, name: KIND_NAME }))
            clearSaveResult()
          }} />
        </div>

        <FormField label="顯示名稱" required span={6} error={fieldErrors?.displayName}
          hint="機構和孩子看到的名字，可以用暱稱">
          <Input required maxLength={100} value={form.displayName} placeholder="王小明"
            onChange={update('displayName')} />
        </FormField>

        <FormField label="連絡電話" optional span={6} error={fieldErrors?.phone}>
          <Input maxLength={40} autoComplete="tel" value={form.phone} onChange={update('phone')} />
        </FormField>
      </div>

      {save.isError && !onlyFieldErrors && (
        <div style={{ marginTop: 16 }}><ErrorNotice error={save.error} /></div>
      )}
      {save.isSuccess && <p className="auth-msg auth-msg--ok" style={{ margin: '16px 0 0' }}>已儲存。</p>}

      <div className="form-actions">
        <button type="submit" className="btn btn--primary" disabled={save.isPending}>
          {save.isPending ? '儲存中…' : '儲存'}
        </button>
      </div>
    </form>
  )
}

/** ③ 參與須知與說明（認領者）：同意紀錄、須知全文、重看「開始之前」。 */
function TermsSection() {
  const terms = useDonorTerms()
  const onboard = useOnboard()
  const status = terms.data

  return (
    <section className="panel">
      <div className="panel__head"><h2 className="panel__title">參與須知與說明</h2></div>

      <dl className="ship-summary">
        <div className="c-full">
          <dt>《{DONOR_TERMS.title}》</dt>
          <dd>
            {!status
              ? '—'
              : status.upToDate
                ? `你已於 ${formatDateTime(status.acceptedAt)} 同意（${DONOR_TERMS.meta.split('・')[0]}）`
                : status.acceptedVersion
                  ? '須知已更新，請重新閱讀並同意目前版本'
                  : '尚未同意目前版本'}
            {' '}
            <DonorTermsLink />
          </dd>
        </div>
      </dl>

      {onboard?.available && (
        <div style={{ marginTop: 16 }}>
          <button type="button" className="btn" onClick={onboard.reopen}>重看「開始之前」說明</button>
        </div>
      )}
    </section>
  )
}

/** ④ 帳號安全：寄送密碼重設信。開發模式沒有密碼。 */
function SecuritySection({ email }: { email: string }) {
  const auth = useAuth()
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const send = async () => {
    setBusy(true)
    setError(null)
    try {
      await auth.sendPasswordReset(email)
      setSent(true)
    } catch (caught) {
      setError(describeAuthError(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel">
      <div className="panel__head"><h2 className="panel__title">帳號安全</h2></div>
      {usingFirebase ? (
        <>
          <button type="button" className="btn" disabled={busy} onClick={() => void send()}>
            {busy ? '寄送中…' : '寄送密碼重設信'}
          </button>
          {sent && <p className="auth-msg auth-msg--ok" style={{ margin: '16px 0 0' }}>密碼重設信已寄出，請查看信箱。</p>}
          {error && <p className="auth-msg auth-msg--danger" style={{ margin: '16px 0 0' }}>{error}</p>}
        </>
      ) : (
        <p style={{ margin: 0, fontSize: 14, color: 'var(--fg-4)' }}>
          開發模式沒有密碼，登入只靠信箱，所以這裡不提供密碼重設。
        </p>
      )}
    </section>
  )
}
