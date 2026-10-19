import { describe, expect, it } from 'vitest'
import {
  formatPhone, formatPhoneKeepingCaret, isCompleteLandline, isCompleteMobile,
} from './phone'

describe('formatPhone', () => {
  it('行動電話：自動加 dash，最多 10 碼', () => {
    expect(formatPhone('0912345678', 'mobile')).toBe('0912-345-678')
    expect(formatPhone('09123', 'mobile')).toBe('0912-3')
    expect(formatPhone('0912-345-6789999', 'mobile')).toBe('0912-345-678')
  })

  it('貼上帶括號與空白的號碼也整理好', () => {
    expect(formatPhone('(04) 2234 5678', 'landline')).toBe('04-2234-5678')
  })

  it('市話：依區碼規則切', () => {
    expect(formatPhone('0222345678', 'landline')).toBe('02-2234-5678')
    expect(formatPhone('049123456', 'landline')).toBe('049-12-3456')
    expect(formatPhone('031234567', 'landline')).toBe('03-123-4567')
    expect(formatPhone('0826123456', 'landline')).toBe('0826-12-3456')
    expect(formatPhone('0836123456', 'landline')).toBe('0836-12-3456')
    expect(formatPhone('0371234567', 'landline')).toBe('037-123-4567')
    expect(formatPhone('0', 'landline')).toBe('0')
    expect(formatPhone('0422', 'landline')).toBe('04-22')
  })
})

describe('formatPhoneKeepingCaret', () => {
  it('在中間插入一個數字時，游標留在那個數字之後', () => {
    // 0912-345-678，在第 5 碼「3」前面插入 9 → 游標應在新的 9 之後
    const typed = '0912-9345-678'
    const result = formatPhoneKeepingCaret(typed, 6, 'mobile')
    expect(result.value).toBe('0912-934-567')
    expect(result.caret).toBe(6)
  })
})

describe('完整性檢查（與後端規則一致）', () => {
  it('行動電話', () => {
    expect(isCompleteMobile('0912-345-678')).toBe(true)
    expect(isCompleteMobile('0812-345-678')).toBe(false)
    expect(isCompleteMobile('0912-345-67')).toBe(false)
  })

  it('市話', () => {
    expect(isCompleteLandline('04-2234-5678')).toBe(true)
    expect(isCompleteLandline('049-12-3456')).toBe(true)
    expect(isCompleteLandline('03-123-4567')).toBe(true)
    expect(isCompleteLandline('04-22')).toBe(false)
    expect(isCompleteLandline('01-2234-5678')).toBe(false)
  })
})
