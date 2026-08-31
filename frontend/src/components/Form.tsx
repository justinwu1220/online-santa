import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'

/*
 * hook class（field-control / field-label / …）不影響亮色外觀，是給深色主題的掛點。
 * 深色差異寫在 index.css 的 .theme-night 規則裡，見 docs/DESIGN.md
 */
const FIELD_CLASS =
  'field-control w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm ' +
  'focus:border-santa-500 focus:outline-none focus:ring-2 focus:ring-santa-100 ' +
  'disabled:bg-slate-50 disabled:text-slate-400'

export function Field({ label, hint, error, required, children }: {
  label: string; hint?: ReactNode; error?: string; required?: boolean; children: ReactNode
}) {
  return (
    <label className="block">
      <span className="field-label text-sm font-medium text-slate-700">
        {label}
        {required && <span className="ml-0.5 text-berry-500">*</span>}
      </span>
      {hint && <span className="field-hint mt-0.5 block text-xs text-slate-500">{hint}</span>}
      <div className="mt-1.5">{children}</div>
      {error && <span className="field-error mt-1 block text-xs text-berry-600">{error}</span>}
    </label>
  )
}

export const TextInput = (props: InputHTMLAttributes<HTMLInputElement>) =>
  <input {...props} className={`${FIELD_CLASS} ${props.className ?? ''}`} />

export const TextArea = (props: TextareaHTMLAttributes<HTMLTextAreaElement>) =>
  <textarea {...props} className={`${FIELD_CLASS} ${props.className ?? ''}`} />

export const Select = (props: SelectHTMLAttributes<HTMLSelectElement>) =>
  <select {...props} className={`${FIELD_CLASS} ${props.className ?? ''}`} />

type ButtonProps = InputHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
  children: ReactNode
  type?: 'button' | 'submit'
}

const VARIANTS = {
  primary: 'bg-santa-600 text-white hover:bg-santa-700 disabled:bg-santa-600/40',
  secondary: 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 disabled:text-slate-300',
  danger: 'bg-berry-500 text-white hover:bg-berry-600 disabled:bg-berry-500/40',
  ghost: 'text-slate-600 hover:bg-slate-100 disabled:text-slate-300',
}

export function Button({ variant = 'primary', children, className, ...props }: ButtonProps) {
  return (
    <button
      {...props}
      className={`btn btn-${variant} inline-flex items-center justify-center gap-1.5
        rounded-lg px-4 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed
        ${VARIANTS[variant]} ${className ?? ''}`}
    >
      {children}
    </button>
  )
}
