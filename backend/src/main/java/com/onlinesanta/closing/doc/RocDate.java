package com.onlinesanta.closing.doc;

import java.time.LocalDate;

/** 民國日期格式。官方 Excel 要 {@code YYY/MM/dd}（文字），報表與 PDF 用「中華民國115年11月25日」。 */
public final class RocDate {

    private RocDate() {
    }

    /** 115/11/25。官方 Excel 的日期欄是文字，不是日期格式。 */
    public static String slash(LocalDate date) {
        return "%03d/%02d/%02d".formatted(date.getYear() - 1911, date.getMonthValue(), date.getDayOfMonth());
    }

    /** 中華民國115年11月25日。 */
    public static String full(LocalDate date) {
        return "中華民國%d年%d月%d日".formatted(date.getYear() - 1911, date.getMonthValue(), date.getDayOfMonth());
    }

    /** 115年11月25日（較短，表格用）。 */
    public static String short_(LocalDate date) {
        return "%d年%d月%d日".formatted(date.getYear() - 1911, date.getMonthValue(), date.getDayOfMonth());
    }
}
