/**
 * 電話格式（設計稿 SPEC 13.4，與後端 `PhoneFormat` 同一套規則）。
 *
 * 只能輸入數字，自動加「-」；資料庫統一存加了 dash 的格式。
 * - 行動電話：09 開頭 10 碼，`0912-345-678`。
 * - 市話（選填）：區碼 02 為 2 碼；0826、0836 為 4 碼；037、049、082、089 為 3 碼；其他（03–08）為 2 碼。
 *   區碼之後 6 碼以上切成「前段-後 4 碼」，例如 `04-2234-5678`、`049-12-3456`、`03-123-4567`。最多 10 碼。
 */
export type PhoneKind = 'mobile' | 'landline'

/** 輸入時即時格式化（還沒打完的號碼也會盡量排好）。 */
export function formatPhone(value: string, kind: PhoneKind): string {
  const digits = value.replace(/\D/g, '').slice(0, 10)
  if (kind === 'mobile') {
    return [digits.slice(0, 4), digits.slice(4, 7), digits.slice(7, 10)].filter(Boolean).join('-')
  }
  const area = /^08[23]6/.test(digits) ? 4 : /^(037|049|082|089)/.test(digits) ? 3 : 2
  const head = digits.slice(0, area)
  const rest = digits.slice(area)
  if (!rest) return head
  if (rest.length < 6) return `${head}-${rest}`
  return `${head}-${rest.slice(0, rest.length - 4)}-${rest.slice(-4)}`
}

/**
 * 格式化後，游標要留在原本那個數字之後（不然每打一個字游標就跳到最後）。
 * @returns 新字串與游標位置
 */
export function formatPhoneKeepingCaret(
  value: string, caret: number | null, kind: PhoneKind,
): { value: string; caret: number } {
  const before = (caret ?? value.length)
  const caretDigits = value.slice(0, before).replace(/\D/g, '').length
  const next = formatPhone(value, kind)
  let pos = 0
  let seen = 0
  while (pos < next.length && seen < caretDigits) {
    if (/\d/.test(next[pos])) seen += 1
    pos += 1
  }
  return { value: next, caret: pos }
}

/** 行動電話完整嗎：09 開頭 10 碼。 */
export function isCompleteMobile(value: string): boolean {
  return /^09\d{8}$/.test(value.replace(/\D/g, ''))
}

/** 市話完整嗎：首碼 0、第二碼 2–8，區碼之後 6–8 碼，含區碼最多 10 碼。 */
export function isCompleteLandline(value: string): boolean {
  const digits = value.replace(/\D/g, '')
  if (digits.length < 8 || digits.length > 10 || !/^0[2-8]/.test(digits)) return false
  const area = /^02/.test(digits) ? 2 : /^08[23]6/.test(digits) ? 4 : /^(037|049|082|089)/.test(digits) ? 3 : 2
  const rest = digits.length - area
  return rest >= 6 && rest <= 8
}
