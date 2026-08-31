import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../lib/api'
import type { OrganizationView, ReleasePolicy } from '../../lib/types'
import { ErrorBanner, Notice } from '../../components/Feedback'
import { Button, Field, TextArea, TextInput } from '../../components/Form'
import { useOrgContext } from './orgContext'

export function OrgSettings() {
  const { organization } = useOrgContext()
  const queryClient = useQueryClient()

  const [form, setForm] = useState({
    name: organization.name,
    contactEmail: organization.contactEmail,
    contactPhone: organization.contactPhone ?? '',
    address: organization.address ?? '',
    description: organization.description ?? '',
    releasePolicy: organization.releasePolicy,
    releaseAfterDays: organization.releaseAfterDays ?? 7,
  })

  const save = useMutation({
    mutationFn: () => api.patch<OrganizationView>('/api/organizations/me', {
      ...form,
      // MANUAL 政策不該帶天數，後端與資料庫的約束都會擋
      releaseAfterDays: form.releasePolicy === 'AUTO' ? form.releaseAfterDays : undefined,
    }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organization'] }),
  })

  const resubmit = useMutation({
    mutationFn: () => api.post<OrganizationView>('/api/organizations/me/resubmit'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organization'] }),
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

  const patchForm = (patch: Partial<typeof form>) => {
    setForm((current) => ({ ...current, ...patch }))
    clearSaveResult()
  }

  const update = (key: keyof typeof form) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    setForm((current) => ({ ...current, [key]: event.target.value }))
    clearSaveResult()
  }

  return (
    <div className="max-w-2xl space-y-8">
      <form
        className="space-y-5"
        onSubmit={(event) => { event.preventDefault(); save.mutate() }}
      >
        <h2 className="font-semibold text-slate-800">機構資料</h2>

        <Field label="機構名稱" required>
          <TextInput required maxLength={120} value={form.name} onChange={update('name')} />
        </Field>
        {/* 兩個聯絡方式併排；地址獨佔一排——它比另外兩者長得多，擠在半排會被截斷 */}
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="聯絡信箱" required>
            <TextInput required type="email" maxLength={255}
              value={form.contactEmail} onChange={update('contactEmail')} />
          </Field>
          <Field label="聯絡電話" required>
            <TextInput required maxLength={40}
              value={form.contactPhone} onChange={update('contactPhone')} />
          </Field>
        </div>

        {/* 必填：捐贈者的認領詳情頁靠它顯示禮物要寄去哪裡 */}
        <Field label="收件地址" required hint="捐贈者會把禮物寄到這裡，請填寫完整地址">
          <TextInput required maxLength={255}
            value={form.address} onChange={update('address')} />
        </Field>
        <Field label="機構簡介">
          <TextArea rows={4} maxLength={2000}
            value={form.description} onChange={update('description')} />
        </Field>

        <hr className="border-santa-100" />

        <h2 className="font-semibold text-slate-800">逾期未寄送的處理</h2>
        <p className="text-sm text-slate-600">
          認領後遲遲未寄送會讓孩子的願望一直卡著。你可以選擇讓系統自動收回，
          或只收到提醒後自行決定。設定變更只影響之後的新認領，不會動到既有的認領。
        </p>

        <div className="space-y-3">
          <PolicyOption
            value="MANUAL"
            current={form.releasePolicy}
            title="我自己決定"
            description="系統只在「逾期提醒」頁標記，由你聯繫捐贈者後決定是否收回。"
            onSelect={(policy) => patchForm({ releasePolicy: policy })}
          />
          <PolicyOption
            value="AUTO"
            current={form.releasePolicy}
            title="自動收回"
            description="超過寬限天數仍未回報寄送，系統自動收回並讓願望重新上架。"
            onSelect={(policy) => patchForm({ releasePolicy: policy })}
          />
        </div>

        {form.releasePolicy === 'AUTO' && (
          <Field label="寬限天數" hint="認領後多少天未寄送就收回（1-60 天）">
            <TextInput type="number" min={1} max={60} required
              value={form.releaseAfterDays}
              onChange={(event) =>
                patchForm({ releaseAfterDays: Number(event.target.value) })} />
          </Field>
        )}

        {save.isError && <ErrorBanner error={save.error} />}
        {save.isSuccess && <Notice tone="success">已儲存。</Notice>}

        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? '儲存中…' : '儲存設定'}
        </Button>
      </form>

      {organization.status === 'REJECTED' && (
        <div className="rounded-xl bg-white p-5 ring-1 ring-santa-100">
          <h2 className="font-semibold text-slate-800">重新送審</h2>
          <p className="mt-1 text-sm text-slate-600">
            資料補齊後可以再次送審，平台會重新檢視。
          </p>
          {resubmit.isError && <div className="mt-3"><ErrorBanner error={resubmit.error} /></div>}
          <Button className="mt-4" disabled={resubmit.isPending} onClick={() => resubmit.mutate()}>
            {resubmit.isPending ? '送出中…' : '重新送審'}
          </Button>
        </div>
      )}
    </div>
  )
}

function PolicyOption({ value, current, title, description, onSelect }: {
  value: ReleasePolicy
  current: ReleasePolicy
  title: string
  description: string
  onSelect: (policy: ReleasePolicy) => void
}) {
  const selected = current === value
  return (
    <label className={`flex cursor-pointer gap-3 rounded-lg border p-4 transition-colors ${
      selected ? 'border-santa-500 bg-santa-50' : 'border-slate-200 bg-white hover:bg-slate-50'
    }`}>
      <input
        type="radio"
        name="releasePolicy"
        className="mt-1"
        checked={selected}
        onChange={() => onSelect(value)}
      />
      <span>
        <span className="block text-sm font-medium text-slate-800">{title}</span>
        <span className="mt-0.5 block text-sm text-slate-600">{description}</span>
      </span>
    </label>
  )
}
