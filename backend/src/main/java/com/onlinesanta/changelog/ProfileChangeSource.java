package com.onlinesanta.changelog;

/**
 * 這筆異動是怎麼來的。
 *
 * <p>爭議裡最先要分清楚的就是「是本人自己改的，還是別人改的」。把來源寫成欄位，
 * 而不是讓人事後比對 actor 與 target 的 id 去推論。
 */
public enum ProfileChangeSource {
    /** 本人（或機構成員）在自己的設定頁改的。 */
    SELF_SERVICE,
    /** 管理員代為改動他人的資料。 */
    ADMIN,
    /** 系統自動改動，沒有人按下送出（例如登入時修補顯示名稱、白名單提升為管理員）。 */
    SYSTEM,
    /** 從舊的 organization_revisions 轉入的紀錄——是重建出來的，不是當下寫的。 */
    LEGACY
}
