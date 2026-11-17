import { describe, expect, it } from 'vitest'
import { ApiError } from './api'
import { describeAuthError } from './authContext'

describe('describeAuthError（登入錯誤對使用者的說明）', () => {
  it('帳號或密碼錯誤：不區分原因', () => {
    const error = new ApiError(401, '信箱或密碼不正確', { errorCode: 'INVALID_CREDENTIALS' })
    expect(describeAuthError(error)).toBe('信箱或密碼不正確')
  })

  it('信箱已註冊', () => {
    expect(describeAuthError(new ApiError(409, 'x', { errorCode: 'EMAIL_TAKEN' })))
      .toBe('這個信箱已經註冊過了，請直接登入')
  })

  it('沒列在對照表的錯誤碼：直接用後端的中文訊息（例如驗證碼剩餘次數）', () => {
    const error = new ApiError(400, '驗證碼不正確，還可以再試 4 次', { errorCode: 'CODE_INVALID' })
    expect(describeAuthError(error)).toBe('驗證碼不正確，還可以再試 4 次')
  })

  it('欄位驗證不過：顯示第一個欄位的訊息', () => {
    const error = new ApiError(400, '請求內容有誤',
      { errorCode: 'VALIDATION_FAILED', fieldErrors: { password: '密碼長度需為 8 到 128 個字元' } })
    expect(describeAuthError(error)).toBe('密碼長度需為 8 到 128 個字元')
  })

  it('網路連不上', () => {
    expect(describeAuthError(new TypeError('Failed to fetch'))).toBe('網路連線有問題，請檢查後再試')
  })

  describe('請求來源網址不在後端允許清單（從 Firebase 預設網址進站就會遇到）', () => {
    const friendly = '這個網址無法登入，請改用官方網站的網址進站後再登入'

    it('更前面的 CORS 檢查回純文字 403：不要把英文原文丟給使用者', () => {
      expect(describeAuthError(new ApiError(403, 'Invalid CORS request'))).toBe(friendly)
    })

    it('我們自己的來源檢查回 ORIGIN_NOT_ALLOWED', () => {
      expect(describeAuthError(new ApiError(403, '不允許的請求來源', { errorCode: 'ORIGIN_NOT_ALLOWED' })))
        .toBe(friendly)
    })

    it('一般的 403（例如帳號被停用）不會被誤判成來源問題', () => {
      const error = new ApiError(403, '此帳號已被停用', { errorCode: 'ACCOUNT_DISABLED' })
      expect(describeAuthError(error)).toBe('此帳號已被停用')
    })
  })
})
