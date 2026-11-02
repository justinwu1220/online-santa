// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ClaimDonorView } from '../../lib/types'
import { AddressPanel } from './ClaimParts'

const claim = {
  childAlias: '小星',
  organizationName: '陽光之家',
  organizationAddress: '台北市中正區某路 1 號',
} as ClaimDonorView

describe('AddressPanel（把禮物寄到這裡）', () => {
  it('列出四點說明，第四點是國內現貨與海外購買的規範', () => {
    render(<AddressPanel claim={claim} done={false} />)
    const items = screen.getAllByRole('listitem')
    expect(items).toHaveLength(4)
    expect(items[3]).toHaveTextContent('請選擇國內現貨，並付清商品及寄送費用後，由國內寄至指定機構。')
    expect(items[3]).toHaveTextContent('請勿將機構或其工作人員列為海外訂單的進口報關人，也不接受貨到付款或運費到付包裹。')
  })

  it('機構沒有地址時不列說明，改顯示提示', () => {
    render(<AddressPanel claim={{ ...claim, organizationAddress: undefined } as ClaimDonorView} done={false} />)
    expect(screen.queryAllByRole('listitem')).toHaveLength(0)
    expect(screen.getByText(/還沒有填寫收件地址/)).toBeInTheDocument()
  })
})
