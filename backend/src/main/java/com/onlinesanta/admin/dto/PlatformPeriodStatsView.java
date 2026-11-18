package com.onlinesanta.admin.dto;

import java.util.List;
import java.util.Map;

import com.onlinesanta.closing.dto.OperatingPeriodView;
import com.onlinesanta.common.dto.PeriodMonthCount;

/**
 * 監控中心的檔期營運總覽。
 *
 * <p>cohort 制：{@code completed} 與 {@code claimOutcomes} 都以該期間<strong>認領</strong>
 * 的筆數為準，不論完成或釋回的實際時間。期間是檔期的營運週期（見
 * {@link com.onlinesanta.closing.OperatingPeriod}）。
 *
 * @param newDonors 該期間新註冊的一般民眾數（以 {@code createdAt} 歸期）
 * @param newOrganizations 該期間新加入的機構數
 * @param activeDonors 該期間至少認領一次的 distinct 捐贈者數
 * @param publishedWishes 該期間新增的願望數（以 {@code createdAt} 歸期，不能用
 *                        {@code publishedAt}——見 {@code Wish.publish()} 的說明）
 * @param monthlyClaims 期間涵蓋的每個月份（含年份、依時間排序，沒有資料補 0）
 * @param claimOutcomes 認領結果分布，僅涵蓋三種終局狀態：COMPLETED／RELEASED／CANCELLED
 * @param topOrganizations 該期間完成認領數前五名的機構，僅管理端可見
 */
public record PlatformPeriodStatsView(
        OperatingPeriodView period,
        long newDonors,
        long newOrganizations,
        long activeDonors,
        long publishedWishes,
        long claimed,
        long completed,
        double completionRate,
        List<PeriodMonthCount> monthlyClaims,
        Map<String, Long> claimOutcomes,
        List<OrganizationCompletionRankingView> topOrganizations) {
}
