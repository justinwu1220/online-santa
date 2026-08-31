package com.onlinesanta.common.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 月份分布，1–12 月已補零。
 *
 * <p>沒有資料的月份不會出現在資料庫的 {@code GROUP BY} 結果裡，補成 0 之後前端才能
 * 穩定畫出完整的長條圖，不必處理「這個月不見了」（比照 {@code AdminStatsService} 對
 * enum 狀態的補零方式，這裡補的是月份）。
 */
public record MonthlyCount(int month, long count) {

    /**
     * @param rows 每列為 {@code [月份（1–12，Number）, 筆數（Number）]}
     */
    public static List<MonthlyCount> fill(List<Object[]> rows) {
        Map<Integer, Long> byMonth = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            byMonth.put(month, 0L);
        }
        for (Object[] row : rows) {
            byMonth.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        return byMonth.entrySet().stream()
                .map(entry -> new MonthlyCount(entry.getKey(), entry.getValue()))
                .toList();
    }
}
