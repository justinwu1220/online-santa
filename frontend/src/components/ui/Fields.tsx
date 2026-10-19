import type { ComponentProps, ReactNode } from 'react'

/**
 * 表單欄位外框：標籤、說明、錯誤訊息（SPEC 11.2）。
 *
 * 預設是 `<label>`，點標籤會聚焦裡面的輸入框。裡面放的不是單一輸入框（例如檔案清單、
 * 多個按鈕）時用 `as="div"`——label 會把範圍內任何點擊轉給裡面第一個可操作元素。
 */
export function FormField({ label, required, optional, hint, hintBeside, error, span, as = 'label', children }: {
  label: string
  required?: boolean
  optional?: boolean
  hint?: ReactNode
  /** 說明放在標題右邊（同一行，放不下才換行），而不是標題下方 */
  hintBeside?: boolean
  error?: string | null
  /** 6 欄格線佔幾欄：6（整列）／4／3／2。沒給就不設定，由外層決定 */
  span?: 2 | 3 | 4 | 6
  as?: 'label' | 'div'
  children: ReactNode
}) {
  const Tag = as
  const labelNode = (
    <span className="field__label">
      {label}
      {required && <span className="req" aria-hidden>*</span>}
      {optional && <span className="opt">選填</span>}
    </span>
  )
  return (
    <Tag className={`field${span ? ` c-${span}` : ''}${error ? ' is-invalid' : ''}`}>
      {hint && hintBeside ? (
        <span className="field__head">{labelNode}<span className="field__hint">{hint}</span></span>
      ) : (
        <>
          {labelNode}
          {hint && <span className="field__hint">{hint}</span>}
        </>
      )}
      {children}
      {error && <span className="field__error" role="alert">{error}</span>}
    </Tag>
  )
}

export function Input(props: ComponentProps<'input'>) {
  return <input {...props} className={`input ${props.className ?? ''}`} />
}

export function TextArea(props: ComponentProps<'textarea'>) {
  return <textarea {...props} className={`input ${props.className ?? ''}`} />
}

export function SelectBox(props: ComponentProps<'select'>) {
  return <select {...props} className={`input ${props.className ?? ''}`} />
}

/** 只能輸入整數、右側固定顯示單位（份、元）。 */
export function UnitInput({ unit, min, max, value, onChange }: {
  unit: string
  min: number
  max: number
  value: string
  onChange: (next: string) => void
}) {
  return (
    <span className="unit-wrap">
      <Input required type="number" inputMode="numeric" step={1} min={min} max={max}
        value={value}
        onKeyDown={(event) => {
          // type="number" 仍允許輸入 e、+、-、小數點
          if (['e', 'E', '+', '-', '.'].includes(event.key)) event.preventDefault()
        }}
        onChange={(event) => onChange(event.target.value.replace(/\D/g, ''))} />
      <span className="unit">{unit}</span>
    </span>
  )
}
