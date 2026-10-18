package com.onlinesanta.wish;

/**
 * 願望的粗粒度狀態，供列表查詢使用（有部分索引支撐搶領當日的瀏覽流量）。
 * 認領的細節流程狀態記在 claims.status，兩者由 ClaimService 在同一交易內同步維護。
 *
 * <p>流程：DRAFT →（機構提交審核）→ PENDING_REVIEW →（管理員核准）→ AVAILABLE → CLAIMED → FULFILLED。
 * 管理員退回、或機構撤回，願望回到 DRAFT。機構不能自行下架，也不能編輯已上架的願望。
 */
public enum WishStatus {
    /** 機構編輯中，尚未公開。 */
    DRAFT,
    /** 機構已提交，等管理員審核；審核期間不能修改（要改請先撤回）。 */
    PENDING_REVIEW,
    /** 已上架，可被認領。 */
    AVAILABLE,
    /** 已被認領，等待寄送與送達。 */
    CLAIMED,
    /** 禮物已送達並完成回饋。 */
    FULFILLED;

    /** 只有草稿能被機構修改內容。 */
    public boolean isEditable() {
        return this == DRAFT;
    }
}
