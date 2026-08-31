/**
 * 平台名稱。
 *
 * 集中在一處，是因為它散落在五個入口的標頭與每一頁的 document.title 裡。哪天要改名
 * （或加上贊助單位），不必去翻十幾個檔案還漏掉一兩處。
 */
export const PLATFORM_NAME = '線上聖誕老公公'

/** 標頭用的品牌字串，帶聖誕樹。 */
export const BRAND = `🎄 ${PLATFORM_NAME}`

/** 瀏覽器分頁標題。`pageTitle()` 不帶參數時就是平台名稱本身。 */
export const pageTitle = (page?: string) =>
  page ? `${page} — ${PLATFORM_NAME}` : PLATFORM_NAME
