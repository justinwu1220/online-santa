package com.onlinesanta.admin.dto;

import java.util.List;
import java.util.Map;

import com.onlinesanta.common.dto.MonthlyCount;

/**
 * 監控中心的年度營運總覽。
 *
 * <p>cohort 制：{@code completed} 與 {@code claimOutcomes} 都以該年度<strong>認領</strong>
 * 的筆數為準，不論完成或釋回的實際時間（見 {@link com.onlinesanta.common.TaiwanYear}）。
 *
 * @param newDonors 該年度新註冊的一般民眾數（以 {@code createdAt} 歸年）
 * @param newOrganizations 該年度新加入的機構數
 * @param activeDonors 該年度至少認領一次的 distinct 捐贈者數
 * @param publishedWishes 該年度新增的願望數（以 {@code createdAt} 歸年，不能用
 *                        {@code publishedAt}——見 {@code Wish.publish()} 的說明）
 * @param claimOutcomes 認領結果分布，僅涵蓋三種終局狀態：COMPLETED／RELEASED／CANCELLED
 * @param topOrganizations 該年度完成認領數前五名的機構，僅管理端可見
 */
public record PlatformAnnualStatsView(
        int year,
        long newDonors,
        long newOrganizations,
        long activeDonors,
        long publishedWishes,
        long claimed,
        long completed,
        double completionRate,
        List<MonthlyCount> monthlyClaims,
        Map<String, Long> claimOutcomes,
        List<OrganizationCompletionRankingView> topOrganizations,
        List<Integer> availableYears) {
}
