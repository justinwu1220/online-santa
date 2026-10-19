// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ShipForm } from './ShipForm'

function renderForm() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <ShipForm claimId="c1" onDone={vi.fn()} />
    </QueryClientProvider>,
  )
}

const field = (label: string) =>
  screen.getByText(label, { selector: '.field__label' }).closest('.field') as HTMLElement
const invalidLabels = () => [...document.querySelectorAll('form .field.is-invalid')]
  .map((f) => f.querySelector('.field__label')?.textContent?.replace('*', '').trim())
const submit = () => fireEvent.click(screen.getByRole('button', { name: '我已經寄出了' }))
const type = (label: string, value: string) =>
  fireEvent.change(field(label).querySelector('input')!, { target: { value } })

describe('ShipForm（回報寄送）必填驗證', () => {
  it('空白送出：每個必填欄位都標紅框與錯誤文字，並捲到第一個錯誤', () => {
    renderForm()
    submit()
    expect(invalidLabels()).toEqual([
      '物流業者', '追蹤碼', '實際寄出的禮物', '數量', '禮物價格', '寄件單或包裹照片',
    ])
    expect(field('追蹤碼').querySelector('.field__error')).toHaveTextContent('請填寫追蹤碼')
    expect(window.Element.prototype.scrollIntoView).toHaveBeenCalled()
  })

  it('開始輸入就清掉那一欄的紅框與錯誤，其他欄維持', () => {
    renderForm()
    submit()

    type('物流業者', '黑貓宅急便')
    type('追蹤碼', 'QA123')
    type('實際寄出的禮物', '色鉛筆')
    type('數量', '1')
    type('禮物價格', '300')

    // 只剩還沒處理的照片
    expect(invalidLabels()).toEqual(['寄件單或包裹照片'])
    expect(field('追蹤碼').querySelector('.field__error')).toBeNull()
  })

  it('勾「我需要捐贈收據」才檢查收據欄位；取消勾選就不檢查', () => {
    renderForm()
    const checkbox = screen.getByRole('checkbox', { name: /我需要捐贈收據/ })

    fireEvent.click(checkbox)
    submit()
    expect(invalidLabels()).toEqual(expect.arrayContaining(['收據抬頭', '身分證字號或統一編號', '收據寄送信箱', '收據寄送地址']))

    fireEvent.click(checkbox)
    submit()
    expect(invalidLabels()).toEqual([
      '物流業者', '追蹤碼', '實際寄出的禮物', '數量', '禮物價格', '寄件單或包裹照片',
    ])
  })

  it('收據欄位也是輸入就清錯誤', () => {
    renderForm()
    fireEvent.click(screen.getByRole('checkbox', { name: /我需要捐贈收據/ }))
    submit()
    expect(invalidLabels()).toContain('收據抬頭')

    type('收據抬頭', '王大明')
    expect(invalidLabels()).not.toContain('收據抬頭')
    expect(invalidLabels()).toContain('收據寄送信箱')
  })

  it('身分證字號格式不對：顯示格式說明', () => {
    renderForm()
    fireEvent.click(screen.getByRole('checkbox', { name: /我需要捐贈收據/ }))
    type('身分證字號或統一編號', 'abc')
    submit()
    expect(field('身分證字號或統一編號').querySelector('.field__error')).toHaveTextContent('格式不正確')
  })
})
