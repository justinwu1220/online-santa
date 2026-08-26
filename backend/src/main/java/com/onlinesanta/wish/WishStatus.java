package com.onlinesanta.wish;

/**
 * 願望的粗粒度狀態，供列表查詢使用（有部分索引支撐搶領當日的瀏覽流量）。
 * 認領的細節流程狀態記在 claims.status，兩者由 ClaimService 在同一交易內同步維護。
 */
public enum WishStatus {
    /** 機構編輯中，尚未公開。 */
    DRAFT,
    /** 已上架，可被認領。 */
    AVAILABLE,
    /** 已被認領，等待寄送與送達。 */
    CLAIMED,
    /** 禮物已送達並完成回饋。 */
    FULFILLED,
    /** 已下架，不再顯示於願望牆。 */
    ARCHIVED;

    /** 只有尚未進入認領流程的願望能被機構修改內容。 */
    public boolean isEditable() {
        return this == DRAFT || this == AVAILABLE || this == ARCHIVED;
    }
}
