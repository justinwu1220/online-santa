package com.onlinesanta.wish;

/**
 * 機構後台把「願望＋它目前的認領」合成一個狀態來看（資料表不合併，這是讀取時算出來的）。
 * 前後端都只認這個值，不各自再從願望狀態與認領狀態推導一次。
 */
public enum WishStage {
    /** 草稿（含被管理員退回的）。 */
    DRAFT,
    /** 已提交，等管理員審核。 */
    PENDING_REVIEW,
    /** 可認領，在願望牆上。 */
    OPEN,
    /** 已被認領，等認領者寄出，還沒逾期。 */
    CLAIMED_WAITING,
    /** 已被認領，超過寄出期限還沒回報寄送。 */
    CLAIMED_OVERDUE,
    /** 認領者已回報寄送，等機構確認收到並完成。 */
    SHIPPED,
    /** 已完成。 */
    DONE,
    /** 願望顯示已被認領卻找不到進行中的認領（理論上不會發生）；不給任何動作，只提示。 */
    INCONSISTENT
}
