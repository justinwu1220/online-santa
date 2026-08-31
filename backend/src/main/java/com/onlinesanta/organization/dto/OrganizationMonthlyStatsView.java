package com.onlinesanta.organization.dto;

import java.util.List;

import com.onlinesanta.common.dto.DailyCount;

/** 機構後台的單月每日認領分布，供年度回顧頁「每月分布」長條圖的下鑽使用。 */
public record OrganizationMonthlyStatsView(int year, int month, List<DailyCount> dailyClaims) {
}
