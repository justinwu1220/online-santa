import { Heart } from 'lucide-react'

/** 「使用善心人士名義」：不想具名的人一鍵把真實姓名填成「善心人士」。註冊與個人資料頁共用。 */
export const KIND_NAME = '善心人士'

export function KindNameButton({ onPick }: { onPick: () => void }) {
  return (
    <button type="button" className="btn btn--sm kind-btn" onClick={onPick}>
      <Heart className="icon" aria-hidden />
      使用{KIND_NAME}名義
    </button>
  )
}
