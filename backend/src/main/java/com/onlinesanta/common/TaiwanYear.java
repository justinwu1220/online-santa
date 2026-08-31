package com.onlinesanta.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.onlinesanta.common.exception.BadRequestException;

/**
 * 年度統計共用的時區與區間換算。
 *
 * <p>「年度」全站統一定義為<strong>台灣時區（Asia/Taipei）的日曆年</strong>，
 * 1 月 1 日至 12 月 31 日。所有時間以 UTC {@code timestamptz} 儲存，換算年度區間時
 * 容易在跨年夜出錯（例如台北 12/31 23:30 認領，UTC 已經是隔年），因此全站的年度統計
 * 一律經由這裡計算，不要各處自算造成邊界不一。
 */
public final class TaiwanYear {

    public static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    private TaiwanYear() {
    }

    /** 某年度的起始（含），對應台北時間 1/1 00:00。 */
    public static Instant startOf(int year) {
        return LocalDate.of(year, 1, 1).atStartOfDay(ZONE).toInstant();
    }

    /** 某年度的結束（不含），即下一年度的起始——所有年度查詢用半開區間 {@code [start, end)}。 */
    public static Instant endOf(int year) {
        return startOf(year + 1);
    }

    /** 目前台北時間所在的年度。 */
    public static int currentYear() {
        return LocalDate.now(ZONE).getYear();
    }

    /** 某個時間點對應的台北年度。 */
    public static int yearOf(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE).getYear();
    }

    /**
     * 某年某月的起始（含），對應台北時間該月 1 號 00:00。
     *
     * <p>用於年度回顧「每月分布」長條圖的下鑽——選定月份後只看該月的每日分布。
     *
     * @throws BadRequestException month 不在 1–12 範圍內
     */
    public static Instant startOfMonth(int year, int month) {
        validateMonth(month);
        return YearMonth.of(year, month).atDay(1).atStartOfDay(ZONE).toInstant();
    }

    /**
     * 某年某月的結束（不含），即下個月的起始——月份查詢比照年度用半開區間
     * {@code [start, end)}。月長交給 {@link YearMonth} 處理，二月是否閏年不必自己判斷。
     *
     * @throws BadRequestException month 不在 1–12 範圍內
     */
    public static Instant endOfMonth(int year, int month) {
        validateMonth(month);
        return YearMonth.of(year, month).plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
    }

    private static void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new BadRequestException("INVALID_MONTH", "月份必須介於 1 到 12");
        }
    }

    /**
     * 由最早一筆紀錄的時間推導可選年份清單，由新到舊排序。
     *
     * <p>沒有任何紀錄時（{@code earliest} 為 null）回傳只含今年的清單——年度下拉選單
     * 永遠至少有一個選項可選，不必處理空清單。
     */
    public static List<Integer> availableYearsSince(Instant earliest) {
        int current = currentYear();
        if (earliest == null) {
            return List.of(current);
        }
        int start = yearOf(earliest);
        List<Integer> years = new ArrayList<>();
        for (int year = current; year >= start; year--) {
            years.add(year);
        }
        return years;
    }
}
