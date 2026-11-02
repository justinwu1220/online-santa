/** 扣繳單位統一編號：8 碼數字（只檢查格式，不驗證檢查碼）。 */
export const TAX_ID_LENGTH = 8

export function isValidTaxId(value: string): boolean {
  return /^\d{8}$/.test(value.trim())
}

/** 輸入時只留數字，最多 8 碼。 */
export function digitsOnly(value: string): string {
  return value.replace(/\D/g, '').slice(0, TAX_ID_LENGTH)
}
