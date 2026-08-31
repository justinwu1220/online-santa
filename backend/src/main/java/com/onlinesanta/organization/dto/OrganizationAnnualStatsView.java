package com.onlinesanta.organization.dto;

import java.util.List;

import com.onlinesanta.common.dto.MonthlyCount;

/**
 * 機構後台的年度回顧。
 *
 * <p>cohort 制：{@code completed}／{@code released}／{@code cancelled} 都只計入該年度
 * <strong>認領</strong>的筆數，不論實際完成或釋回的時間——年末認領、隔年才完成的禮物
 * 仍算在認領年度（見 {@link com.onlinesanta.common.TaiwanYear}）。近年度的數字會隨進行中
 * 的認領逐漸收斂，{@code crossYearCompletions} 量化了這個拖尾規模。
 *
 * @param released 釋回總數，涵蓋機構手動收回與逾期自動釋回兩種
 * @param autoReleasedCount 前述釋回中，屬於逾期自動釋回（{@code claim_events} 的
 *                          {@code RELEASED_AUTO}）的次數
 * @param averageCompletionDays 平均完成天數（認領到完成），沒有任何完成筆數時為 null
 * @param crossYearCompletions 該年度認領、但完成時間落在隔年（或更晚）的筆數
 */
public record OrganizationAnnualStatsView(
        int year,
        long newWishes,
        long claimed,
        long completed,
        double completionRate,
        long released,
        long cancelled,
        long autoReleasedCount,
        Double averageCompletionDays,
        long crossYearCompletions,
        List<MonthlyCount> monthlyClaims,
        List<Integer> availableYears) {
}
