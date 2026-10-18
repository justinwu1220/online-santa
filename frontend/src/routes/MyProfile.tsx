import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronRight } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../lib/api'
import { describeAuthError, useAuth } from '../lib/authContext'
import { pageTitle } from '../lib/brand'
import { useMyProfile } from '../lib/useMyProfile'
import type { UserProfile } from '../lib/types'
import { FormField, Input } from '../components/ui/Fields'
import { ErrorNotice, Spinner } from '../components/ui/Spinner'

/**
 * 個人檔案設定頁。
 *
 * 用戶名稱會在認領後以 donorName 顯示給機構（見 ClaimOrgView / AdminClaimView），
 * 這裡的欄位提示要讓使用者先知道這件事，而不是事後才發現。
 */
export function MyProfile() {
  const { email } = useAuth()
  const profile = useMyProfile()

  useEffect(() => {
    document.title = pageTitle('個人資料')
    return () => { document.title = pageTitle() }
  }, [])

  if (!email) {
    return <div className="notice notice--info" style={{ marginTop: 40 }}>請先在右上角登入，才能查看個人資料。</div>
  }

  return (
    <section style={{ maxWidth: 720 }}>
      <nav className="crumbs" aria-label="麵包屑">
        <Link to="/">願望牆</Link>
        <ChevronRight className="icon" size={14} aria-hidden />
        <span aria-current="page">個人資料</span>
      </nav>

      <h1 style={{ margin: '0 0 24px', fontSize: 34, fontWeight: 900, letterSpacing: '.04em' }}>個人資料</h1>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
        {profile.isLoading && <Spinner label="載入個人資料" />}
        {profile.isError && <ErrorNotice error={profile.error} onRetry={() => void profile.refetch()} />}

        {profile.data && (
          <>
            <ProfileForm profile={profile.data} />
            <EmailSection profile={profile.data} />
            <SecuritySection email={email} />
          </>
        )}
      </div>
    </section>
  )
}

function ProfileForm({ profile }: { profile: UserProfile }) {
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

  return (
    <form className="panel" onSubmit={(event) => { event.preventDefault(); save.mutate() }}>
      <div className="panel__head"><h2 className="panel__title">基本資料</h2></div>

      <div className="form-grid">
        <div className="c-6" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <FormField label="真實姓名" optional error={fieldErrors?.name}
            hint="用於捐贈收據與向衛福部申報的徵信名冊，不會顯示在網站上">
            <Input maxLength={100} autoComplete="name" value={form.name} onChange={update('name')} />
          </FormField>
          <button type="button" className="btn btn--sm kind-btn"
            onClick={() => { setForm((current) => ({ ...current, name: '善心人士' })); clearSaveResult() }}>
            使用善心人士名義
          </button>
        </div>

        <FormField label="用戶名稱" required span={6} error={fieldErrors?.displayName}
          hint="機構和孩子看到的名字，可以用暱稱">
          <Input required maxLength={100} value={form.displayName} onChange={update('displayName')} />
        </FormField>

        <FormField label="連絡電話" optional span={6} error={fieldErrors?.phone}>
          <Input maxLength={40} value={form.phone} onChange={update('phone')} />
        </FormField>
      </div>

      {save.isError && <div style={{ marginTop: 16 }}><ErrorNotice error={save.error} /></div>}
      {save.isSuccess && <p className="auth-msg auth-msg--ok" style={{ margin: '16px 0 0' }}>已儲存。</p>}

      <div className="form-actions">
        <button type="submit" className="btn btn--primary" disabled={save.isPending}>
          {save.isPending ? '儲存中…' : '儲存'}
        </button>
      </div>
    </form>
  )
}

function EmailSection({ profile }: { profile: UserProfile }) {
  const auth = useAuth()
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const resend = async () => {
    setBusy(true)
    setError(null)
    try {
      await auth.resendVerification()
      setSent(true)
    } catch (caught) {
      setError(describeAuthError(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="panel">
      <div className="panel__head"><h2 className="panel__title">Email</h2></div>

      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12 }}>
        <span style={{ color: 'var(--fg-3)', overflowWrap: 'anywhere' }}>{profile.email}</span>
        <span className="nav-count" style={{
          margin: 0, background: profile.emailVerified ? '#15803d' : '#facc15',
          color: profile.emailVerified ? '#fff' : '#422006',
        }}>
          {profile.emailVerified ? '已驗證' : '未驗證'}
        </span>
      </div>

      {!profile.emailVerified && (
        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div><button type="button" className="btn" disabled={busy} onClick={() => void resend()}>
            {busy ? '寄送中…' : '重新寄送驗證信'}
          </button></div>
          {sent && <p className="auth-msg auth-msg--ok" style={{ margin: 0 }}>驗證信已重新寄出，請查看信箱（記得檢查垃圾郵件匣）。</p>}
          {error && <p className="auth-msg" style={{ margin: 0 }}>{error}</p>}
        </div>
      )}
    </div>
  )
}

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
    <div className="panel">
      <div className="panel__head"><h2 className="panel__title">帳號安全</h2></div>
      <button type="button" className="btn" disabled={busy} onClick={() => void send()}>
        {busy ? '寄送中…' : '寄送密碼重設信'}
      </button>
      {sent && <p className="auth-msg auth-msg--ok" style={{ margin: '16px 0 0' }}>密碼重設信已寄出，請查看信箱。</p>}
      {error && <p className="auth-msg" style={{ margin: '16px 0 0' }}>{error}</p>}
    </div>
  )
}
