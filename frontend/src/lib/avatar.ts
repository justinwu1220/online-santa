/**
 * 兒童頭貼（SPEC 14.1）。
 *
 * 頭貼在建立願望時由後端挑一次並存進資料庫（`wishes.avatar`，見後端 `WishAvatar`），
 * 列表、詳細頁、我的認領都回同一個檔名；前端只負責把檔名換成網址。
 * 檔案放在 `public/avatars/`。
 */
export function avatarSrc(avatar: string): string {
  return `/avatars/${avatar}`
}
