package com.onlinesanta.admin.dto;

import java.util.List;

import com.onlinesanta.common.dto.DailyCount;

/** 監控中心的單月每日認領分布，供年度營運頁「每月趨勢」長條圖的下鑽使用。 */
public record PlatformMonthlyStatsView(int year, int month, List<DailyCount> dailyClaims) {
}
