/**
 * 機器欄位名 → 中文標籤。
 *
 * 後端的異動紀錄刻意存機器欄位名（標籤日後被改寫時，既有證據的意思不該跟著變），
 * 所以翻譯在這裡做。查不到的欄位一律原樣顯示，不要讓畫面出現空白。
 */

/** 機構可編輯的 13 個欄位，與後端 ProfileSnapshots.of(Organization) 的鍵一致。 */
export const ORGANIZATION_FIELD_LABELS: Record<string, string> = {
  name: '機構名稱',
  contactPerson: '承辦人',
  responsiblePerson: '負責人',
  withholdingTaxId: '扣繳單位統一編號',
  contactEmail: '聯絡信箱',
  contactPhone: '聯絡電話',
  mobilePhone: '行動電話',
  address: '地址',
  preferredDeliveryTime: '方便收件時間',
  description: '簡介',
  website: '官網',
  releasePolicy: '逾期收回方式',
  releaseAfterDays: '逾期收回天數',
}

/** 認領者帳號會變動的欄位，與後端 ProfileSnapshots.of(User) 的鍵一致。 */
export const USER_FIELD_LABELS: Record<string, string> = {
  displayName: '顯示名稱',
  name: '真實姓名',
  phone: '聯絡電話',
  firebaseUid: '登入識別碼',
  role: '角色',
  organizationId: '所屬機構',
}

/** 兩邊合起來，給不分對象的清單用（同名欄位以機構的說法為準，兩者語意相同）。 */
export const FIELD_LABELS: Record<string, string> = {
  ...USER_FIELD_LABELS,
  ...ORGANIZATION_FIELD_LABELS,
}

export function fieldLabel(field: string, labels: Record<string, string> = FIELD_LABELS): string {
  return labels[field] ?? field
}
