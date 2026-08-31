package com.onlinesanta.claim.dto;

import java.util.List;

/**
 * 捐贈者的年度小結。
 *
 * <p>cohort 制：{@code completedCount} 只計入該年度<strong>認領</strong>且已完成的筆數
 * ——年末認領、隔年才完成的禮物仍算在認領年度，因此進行中的認領完成後，去年的完成數
 * 會隨之上升（見 {@link com.onlinesanta.common.TaiwanYear}）。
 *
 * @param childrenHelped 送禮的孩子數，以 distinct 願望計（同一願望不會重複計入）
 * @param organizationsSupported 支持的機構數，以 distinct 機構計
 * @param availableYears 有認領紀錄可選的年份，由新到舊排序
 */
public record DonorAnnualSummaryView(
        int year,
        long claimedCount,
        long completedCount,
        long childrenHelped,
        long organizationsSupported,
        List<Integer> availableYears) {
}
