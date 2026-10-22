import { useCurrentUser } from '../../lib/useCurrentUser'

/**
 * 認領者被停權（受限登入）的提示列：全站顯示在頁首下方。被停權的人仍可登入、查看自己的認領，
 * 但所有寫入動作（認領、傳訊、回報寄送、修改資料）都會被後端擋下——這條提示說明為什麼。
 */
export function SuspendedBar() {
  const me = useCurrentUser()
  if (!me.data?.suspended) return null
  return (
    <div className="bar bar--dev" role="status">
      <strong>你的帳號目前受到限制</strong>
      {me.data.suspendedReason && <>：{me.data.suspendedReason}</>}
      。你仍可查看自己的認領，但無法認領新的願望、傳送訊息、回報寄送或修改資料，進行中的認領也暫停處理。
      若有問題可聯絡：onlinesanta2026@gmail.com
    </div>
  )
}
