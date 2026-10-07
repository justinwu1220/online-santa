package com.onlinesanta.wish;

/**
 * 願望牆公開可篩選的狀態子集。
 *
 * <p>只有這三個值——{@code DRAFT} 與 {@code ARCHIVED} 不開放篩選，因此不是這裡的常數。
 * 用獨立的列舉而不是直接重用 {@link WishStatus} 當 {@code @RequestParam} 型別，是為了讓
 * 「傳入 DRAFT/ARCHIVED」這種請求在繫結階段就被 Spring 擋成 400，不需要額外寫驗證邏輯。
 */
public enum WishWallStatus {
    AVAILABLE, CLAIMED, FULFILLED;

    public WishStatus toWishStatus() {
        return WishStatus.valueOf(name());
    }
}
