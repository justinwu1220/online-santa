package com.onlinesanta.common.dto;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 營運週期內的月份分布，含年份、依時間排序，沒有資料的月份補 0。
 *
 * <p>週期可能跨年（例如 11 月到隔年 1 月），所以不能像 {@link MonthlyCount} 只用 1–12 月：
 * 那樣會把兩年的同一個月份疊在一起。
 */
public record PeriodMonthCount(int year, int month, long count) {

    /**
     * @param months 週期涵蓋的月份（依時間排序）
     * @param rows   每列為 {@code [年, 月, 筆數]}（Number）；落在 {@code months} 之外的列忽略
     */
    public static List<PeriodMonthCount> fill(List<YearMonth> months, List<Object[]> rows) {
        Map<YearMonth, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                    ((Number) row[2]).longValue());
        }
        return months.stream()
                .map(m -> new PeriodMonthCount(m.getYear(), m.getMonthValue(), counts.getOrDefault(m, 0L)))
                .toList();
    }
}
