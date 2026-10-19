import { Heart } from 'lucide-react'
import { reducedMotion } from '../../lib/motion'

/** 「使用善心人士名義」：不想具名的人一鍵把真實姓名填成「善心人士」。註冊與個人資料頁共用。 */
export const KIND_NAME = '善心人士'

export function KindNameButton({ onPick }: { onPick: () => void }) {
  return (
    <button type="button" className="btn btn--sm kind-btn" onClick={(event) => {
      const button = event.currentTarget
      onPick()
      // 值更新後：聚焦並全選姓名欄，欄位外圈閃一下紅色光暈
      window.setTimeout(() => {
        const input = button.parentElement?.querySelector<HTMLInputElement>('input')
        if (!input) return
        input.focus()
        input.setSelectionRange(0, input.value.length)
        if (!reducedMotion()) {
          input.animate([{ boxShadow: '0 0 0 3px rgba(248,113,113,.45)' }, { boxShadow: '0 0 0 3px rgba(248,113,113,0)' }],
            { duration: 900, easing: 'ease-out' })
        }
      }, 0)
    }}>
      <Heart className="icon" aria-hidden />
      使用{KIND_NAME}名義
    </button>
  )
}
