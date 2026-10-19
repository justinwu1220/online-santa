import { useRef } from 'react'
import { formatPhoneKeepingCaret, type PhoneKind } from '../../lib/phone'
import { Input } from './Fields'

/**
 * 電話輸入框（設計稿 13.4）：只能輸入數字，自動加「-」，游標不會因為重新排版而跳到最後。
 * 行動電話與市話用同一個元件，差別只在 `kind`。
 */
export function PhoneInput({ kind, value, onChange, placeholder, required, id }: {
  kind: PhoneKind
  value: string
  onChange: (next: string) => void
  placeholder?: string
  required?: boolean
  id?: string
}) {
  const ref = useRef<HTMLInputElement>(null)
  return (
    <Input ref={ref} id={id} type="tel" inputMode="numeric" autoComplete="tel"
      required={required} placeholder={placeholder} value={value}
      onChange={(event) => {
        const input = event.target
        const next = formatPhoneKeepingCaret(input.value, input.selectionStart, kind)
        onChange(next.value)
        // 值更新後 React 會重設游標，等這一輪畫完再放回去
        requestAnimationFrame(() => ref.current?.setSelectionRange(next.caret, next.caret))
      }} />
  )
}
