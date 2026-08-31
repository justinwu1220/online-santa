package com.onlinesanta.common.dto;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 單月的每日分布，1 號到當月最後一天已補零。
 *
 * <p>當月天數由 {@link YearMonth} 推導（閏年二月交給它判斷，不寫死 31）。比照
 * {@link MonthlyCount} 對月份的補零方式，這裡補的是日期——沒有資料的日子不會出現在
 * {@code GROUP BY} 的結果裡，補成 0 之後前端才能穩定畫出完整長度的長條圖。
 */
public record DailyCount(int day, long count) {

    /**
     * @param rows 每列為 {@code [日期（1–月底，Number）, 筆數（Number）]}
     */
    public static List<DailyCount> fill(int year, int month, List<Object[]> rows) {
        int daysInMonth = YearMonth.of(year, month).lengthOfMonth();
        Map<Integer, Long> byDay = new LinkedHashMap<>();
        for (int day = 1; day <= daysInMonth; day++) {
            byDay.put(day, 0L);
        }
        for (Object[] row : rows) {
            byDay.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        return byDay.entrySet().stream()
                .map(entry -> new DailyCount(entry.getKey(), entry.getValue()))
                .toList();
    }
}
