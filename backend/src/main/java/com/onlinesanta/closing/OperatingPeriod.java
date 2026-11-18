package com.onlinesanta.closing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.onlinesanta.common.TaiwanYear;

/**
 * 營運統計的一個期間。
 *
 * <p>一個檔期對應一個營運週期：上一個檔期勸募結束的隔天，到本檔期勸募結束日（含）。
 * 這樣所有日期都剛好落在某個週期，沒有重疊也沒有空檔；檔期前的機構註冊、建立願望也算進該檔期。
 * 最後一個檔期結束之後的資料屬於「尚未建立檔期」（{@code campaignId} 為 null、key 為 {@value #UNASSIGNED}）。
 *
 * @param start 週期第一天（台北日期，含）
 * @param end   週期最後一天（台北日期，含）
 * @param from  查詢用起點（含）；第一個週期用 {@link Instant#EPOCH}，確保更早的資料不會漏掉
 * @param to    查詢用終點（不含），即 {@code end} 隔天的台北 00:00
 */
public record OperatingPeriod(
        String key,
        UUID campaignId,
        String name,
        LocalDate start,
        LocalDate end,
        Instant from,
        Instant to) {

    public static final String UNASSIGNED = "unassigned";

    public boolean unassigned() {
        return campaignId == null;
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(start) && !day.isAfter(end);
    }

    /** 週期涵蓋的每個月份（依時間排序），圖表用來補零，跨年時不會把兩年的同月份疊在一起。 */
    public List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = YearMonth.from(start); !month.isAfter(YearMonth.from(end)); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    static OperatingPeriod of(String key, UUID campaignId, String name, LocalDate start, LocalDate end,
                              boolean first) {
        return new OperatingPeriod(key, campaignId, name, start, end,
                first ? Instant.EPOCH : TaiwanYear.startOfDay(start), TaiwanYear.startOfDay(end.plusDays(1)));
    }
}
