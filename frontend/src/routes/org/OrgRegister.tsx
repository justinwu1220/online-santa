import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError, api } from '../../lib/api'
import type { OrganizationView } from '../../lib/types'
import { ErrorBanner, Notice } from '../../components/Feedback'
import { Button, Field, TextArea, TextInput } from '../../components/Form'

/** 機構自助註冊。送出後為待審核，須經平台管理員核准才能上架願望。 */
export function OrgRegister() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    name: '', contactEmail: '', contactPhone: '', address: '', description: '',
  })

  const register = useMutation({
    mutationFn: () => api.post<OrganizationView>('/api/organizations', form),
    onSuccess: () => {
      // 註冊者會從 DONOR 變成 ORG_MEMBER，身分要重新載入
      void queryClient.invalidateQueries({ queryKey: ['me'] })
      void queryClient.invalidateQueries({ queryKey: ['organization'] })
    },
  })

  const update = (key: keyof typeof form) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => setForm((current) => ({ ...current, [key]: event.target.value }))

  const fieldErrors = register.error instanceof ApiError ? register.error.fieldErrors : undefined

  return (
    <section className="mx-auto max-w-2xl space-y-6 px-4 py-10">
      <div>
        <h1 className="text-3xl font-bold text-santa-700">機構註冊</h1>
        <p className="mt-2 text-slate-600">
          註冊後平台會審核你的機構資料，核准後即可上架孩子的願望。
        </p>
      </div>

      <Notice tone="warning">
        <p className="font-medium">送出前請先確認這個帳號的用途</p>
        <p className="mt-1">
          成為機構成員後，<strong>這個帳號將無法再以個人身分認領願望</strong>——
          一個帳號只能有一種身分。如果你也想以個人身分參與，建議機構改用另一個
          聯絡信箱註冊。
        </p>
      </Notice>

      <Notice>
        審核是必要的把關——上架的是孩童資料，不能讓任何人自稱機構就能發布。
      </Notice>

      <form
        className="space-y-5"
        onSubmit={(event) => { event.preventDefault(); register.mutate() }}
      >
        <Field label="機構名稱" required error={fieldErrors?.name}>
          <TextInput required maxLength={120} value={form.name} onChange={update('name')} />
        </Field>
        <Field label="聯絡信箱" required error={fieldErrors?.contactEmail}>
          <TextInput required type="email" maxLength={255}
            value={form.contactEmail} onChange={update('contactEmail')} />
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

        {register.isError && <ErrorBanner error={register.error} />}

        <Button type="submit" disabled={register.isPending}>
          {register.isPending ? '送出中…' : '送出註冊申請'}
        </Button>
      </form>
    </section>
  )
}
