import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { ApiError, api } from '../../lib/api'
import { useAuth } from '../../lib/authContext'
import { pageTitle } from '../../lib/brand'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import { ACCEPTED_ORG_DOCUMENT_TYPES, uploadImage, validateOrgDocument } from '../../lib/upload'
import type { OrganizationView } from '../../lib/types'
import { EmailVerificationBanner } from '../../components/EmailVerificationBanner'
import { WrongAccountPanel } from '../../components/auth/WrongAccountPanel'
import { ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { Button, Field, TextArea, TextInput } from '../../components/Form'
import { StepHeading } from './applyShared'

const MAX_DOCUMENTS = 3

type Form = {
  name: string
  contactPerson: string
  contactEmail: string
  contactPhone: string
  address: string
  description: string
}

type Draft = { form: Form; contactEmailTouched: boolean }

const EMPTY: Draft = {
  form: {
    name: '', contactPerson: '', contactEmail: '',
    contactPhone: '', address: '', description: '',
  },
  contactEmailTouched: false,
}

const draftKeyFor = (email: string) => `org-apply-draft:${email}`

function readDraft(key: string): Draft {
  try {
    const saved = sessionStorage.getItem(key)
    if (!saved) return EMPTY
    const parsed = JSON.parse(saved) as Partial<Draft>
    return {
      form: { ...EMPTY.form, ...parsed.form },
      contactEmailTouched: Boolean(parsed.contactEmailTouched),
    }
  } catch {
    // 讀不到或內容壞掉就當作沒有草稿——不要讓它變成白畫面
    return EMPTY
  }
}

/**
 * 機構申請的步驟二：機構資料。
 *
 * <p>這一層只做身分分流。表單在 ApplyForm 裡，並以帳號信箱當 key——換帳號時整個重新
 * 掛載，草稿才不會跨帳號互相汙染。
 */
export function OrgApply() {
  const auth = useAuth()
  const me = useCurrentUser()

  useEffect(() => {
    document.title = pageTitle('機構申請')
    return () => { document.title = pageTitle() }
  }, [])

  if (auth.loading) return <Spinner />
  // 還沒有帳號：回步驟一
  if (!auth.email) return <Navigate to="/org/register" replace />

  if (me.isLoading) return <Spinner label="確認身分" />
  if (me.isError) {
    return (
      <Shell>
        <ErrorBanner error={me.error} onRetry={() => void me.refetch()} />
      </Shell>
    )
  }

  const role = effectiveRoleOf(me.data)
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

  return <ApplyForm key={auth.email} email={auth.email} />
}

/**
 * 機構資料表單。
 *
 * <p>密碼註冊的人會在這一頁等驗證信，而在同一個分頁點驗證信連結會離開頁面——所以內容
 * 存進 sessionStorage，回來時還原。這是拆成兩頁唯一的實質代價，不處理的話使用者會白
 * 打一次整張表單。
 */
function ApplyForm({ email }: { email: string }) {
  const auth = useAuth()
  const queryClient = useQueryClient()

  const draftKey = draftKeyFor(email)
  // 在初始化函式裡讀，而不是在 effect 裡 setState——後者會多一次 render，
  // 而且第一幀會閃過空白的表單
  const [draft, setDraft] = useState<Draft>(() => readDraft(draftKey))
  const { form, contactEmailTouched } = draft

  // 文件不進草稿（File 物件無法存進 sessionStorage），送出前都還是本地狀態，
  // 離開這頁或重新整理會遺失已選的檔案——跟表單文字欄位不同，先記錄為已知限制
  const [documents, setDocuments] = useState<File[]>([])
  const [documentsError, setDocumentsError] = useState<string | null>(null)
  const documentInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    try {
      sessionStorage.setItem(draftKey, JSON.stringify(draft))
    } catch {
      // 存不了就算了，只是重新整理後要重打
    }
  }, [draft, draftKey])

  const contactEmail = contactEmailTouched ? form.contactEmail : email

  const register = useMutation({
    mutationFn: async () => {
      const organization = await api.post<OrganizationView>(
        '/api/organizations', { ...form, contactEmail })
      // 機構在送出前沒有 id，文件只能在機構建立之後才上傳。這裡的檔案若有一份
      // 上傳失敗，機構申請本身已經成立（不會、也不該回滾）——使用者會看到錯誤，
      // 但機構其實已經送出去了，這個落差目前沒有「重新上傳」的介面可以補救
      for (const file of documents) {
        await uploadImage('ORG_DOCUMENT', organization.id, file)
      }
      return organization
    },
    onSuccess: () => {
      try {
        sessionStorage.removeItem(draftKey)
      } catch { /* 清不掉也無所謂，下次進來會被覆寫 */ }
      // 註冊者會從 DONOR 變成 ORG_MEMBER，身分要重新載入。
      //
      // 這裡刻意不自己 navigate('/org')：身分重新載入是非同步的，馬上導過去的話
      // /org 的 RequireRole 讀到的還是舊的 DONOR，會把人彈回登入頁再彈回來。
      // 上層的角色分流看到 ORG_MEMBER 就會自動導向，等身分真的更新了才走。
      void queryClient.invalidateQueries({ queryKey: ['me'] })
      void queryClient.invalidateQueries({ queryKey: ['organization'] })
    },
  })

  const update = (key: keyof Form) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => setDraft((current) => ({
    ...current,
    form: { ...current.form, [key]: event.target.value },
  }))

  const fieldErrors = register.error instanceof ApiError ? register.error.fieldErrors : undefined

  if (register.isSuccess) {
    return <Shell><Spinner label="申請已送出，正在進入機構後台" /></Shell>
  }

  return (
    <>
      {/* 這一頁不在任何 layout 底下，橫幅要自己帶——密碼註冊的人正是在這裡等驗證信，
          而「我已經驗證好了」那顆按鈕就在橫幅上 */}
      <EmailVerificationBanner />

      <Shell>
        <StepHeading step={2} title="機構資料" />

        <p className="text-sm text-slate-600">
          以 <strong>{email}</strong> 的身分申請。送出後由平台審核，核准前可以先建立
          願望草稿。
        </p>

        <Notice tone="warning">
          成為機構成員後，<strong>這個帳號將無法再以個人身分認領願望</strong>——
          一個帳號只能有一種身分。如果你也想以個人身分參與，建議機構改用另一個
          聯絡信箱註冊。
        </Notice>

        <form
          className="space-y-5"
          onSubmit={(event) => {
            event.preventDefault()
            if (!auth.emailVerified) return
            register.mutate()
          }}
        >
          <Field label="機構名稱" required error={fieldErrors?.name}>
            <TextInput required maxLength={120} placeholder="某某社會福利基金會"
              value={form.name} onChange={update('name')} />
          </Field>

          <Field label="承辦人姓名" required error={fieldErrors?.contactPerson}
            hint="平台審核與捐贈者聯繫時的窗口">
            <TextInput required maxLength={100} placeholder="王小明"
              value={form.contactPerson} onChange={update('contactPerson')} />
          </Field>

          <Field label="聯絡信箱" required error={fieldErrors?.contactEmail}
            hint={contactEmailTouched ? undefined : '預設與帳號信箱相同，可以改'}>
            <TextInput required type="email" maxLength={255} value={contactEmail}
              onChange={(event) => setDraft((current) => ({
                contactEmailTouched: true,
                form: { ...current.form, contactEmail: event.target.value },
              }))} />
          </Field>

          {/* 電話與地址必填：捐贈者認領之後會在認領詳情頁看到它們，並照著寄送 */}
          <Field label="聯絡電話" required error={fieldErrors?.contactPhone}>
            <TextInput required maxLength={40} placeholder="02-1234-5678"
              value={form.contactPhone} onChange={update('contactPhone')} />
          </Field>

          <Field label="收件地址" required error={fieldErrors?.address}
            hint="捐贈者會把禮物寄到這裡，請填寫完整地址">
            <TextInput required maxLength={255} placeholder="台北市中正區某某路 1 號"
              value={form.address} onChange={update('address')} />
          </Field>

          <Field label="機構簡介" hint="讓捐贈者了解你們服務的對象"
            error={fieldErrors?.description}>
            <TextArea rows={4} maxLength={2000}
              value={form.description} onChange={update('description')} />
          </Field>

          <Field label="相關文件證明"
            hint="請擇一上傳證明文件:法人登記證書/立案證書/設立許可函/教職員證/當學年度聘書"
            error={documentsError ?? undefined}>
            <input
              ref={documentInputRef}
              type="file"
              className="hidden"
              accept={ACCEPTED_ORG_DOCUMENT_TYPES.join(',')}
              onChange={(event) => {
                const file = event.target.files?.[0]
                if (!file) return
                if (documents.length >= MAX_DOCUMENTS) {
                  setDocumentsError(`最多上傳 ${MAX_DOCUMENTS} 份文件`)
                } else {
                  const problem = validateOrgDocument(file)
                  if (problem) {
                    setDocumentsError(problem)
                  } else {
                    setDocuments((current) => [...current, file])
                    setDocumentsError(null)
                  }
                }
                event.target.value = ''
              }}
            />
            <div className="space-y-2">
              {documents.map((file, index) => (
                <div key={`${file.name}-${index}`}
                  className="flex items-center justify-between gap-2 rounded-lg border
                    border-slate-200 bg-white px-3 py-2 text-sm">
                  <span className="truncate text-slate-700">{file.name}</span>
                  <button type="button" className="text-slate-400 hover:text-berry-600"
                    onClick={() => setDocuments((current) => current.filter((_, i) => i !== index))}>
                    移除
                  </button>
                </div>
              ))}
              {documents.length < MAX_DOCUMENTS && (
                <Button type="button" variant="secondary"
                  onClick={() => documentInputRef.current?.click()}>
                  新增文件
                </Button>
              )}
            </div>
          </Field>

          {!auth.emailVerified && (
            <Notice tone="warning">
              <p className="font-medium">送出前要先完成信箱驗證</p>
              <p className="mt-1">
                機構申請通過後就能上架孩童資料，門檻不能只是「填了一個信箱」。
                驗證信寄到 <strong>{email}</strong>，點完連結後回到這一頁，按上方橫幅的
                「我已經驗證好了」就能送出。
                <strong>你填的資料會留著，離開這一頁也不會消失。</strong>
              </p>
            </Notice>
          )}

          {register.isError && <ErrorBanner error={register.error} />}

          <Button type="submit" className="w-full py-2.5"
            disabled={register.isPending || !auth.emailVerified}>
            {register.isPending ? '送出中…' : '送出申請'}
          </Button>
        </form>
      </Shell>
    </>
  )
}

function Shell({ children }: { children: React.ReactNode }) {
  return <div className="mx-auto max-w-2xl space-y-6 px-4 py-10">{children}</div>
}
