import { describe, expect, it } from 'vitest'
import { digitsOnly, isValidTaxId } from './taxId'

describe('扣繳單位統一編號', () => {
  it('只有 8 碼數字才合格', () => {
    expect(isValidTaxId('12345678')).toBe(true)
    expect(isValidTaxId(' 12345678 ')).toBe(true)
    for (const bad of ['', '1234567', '123456789', 'ABCDEFGH', '1234 5678', '1234567a']) {
      expect(isValidTaxId(bad)).toBe(false)
    }
  })

  it('輸入時濾掉非數字並限制 8 碼', () => {
    expect(digitsOnly('12-34 56ab78999')).toBe('12345678')
    expect(digitsOnly('abc')).toBe('')
  })
})
