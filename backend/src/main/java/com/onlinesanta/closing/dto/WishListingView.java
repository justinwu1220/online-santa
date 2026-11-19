package com.onlinesanta.closing.dto;

import java.time.LocalDate;

/**
 * 機構現在能不能新增願望。
 *
 * @param open        目前有進行中的檔期且今天落在它的上架期間
 * @param windowStart 開放時是目前上架期間的起日；未開放時是下一次上架期間的起日（沒有就是 null）
 * @param windowEnd   同上，迄日
 */
public record WishListingView(boolean open, LocalDate windowStart, LocalDate windowEnd) {
}
