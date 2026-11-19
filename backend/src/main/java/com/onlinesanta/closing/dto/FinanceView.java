package com.onlinesanta.closing.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 檔期的財務資料（現金實募、利息、募得款實支、自籌）。金額欄位空白（null）＝待補，0＝已核實沒有發生。
 * 同一個結構用於讀取與儲存。
 *
 * @param months 勸募開始日到財物使用結束日涵蓋的每個月份（讀取時補齊，儲存時只會保存有填寫的月份）
 */
public record FinanceView(
        @Valid List<Month> months,
        @Min(value = 0, message = "自籌金額不可為負") Long selfFundedAmount,
        @Size(max = 500, message = "自籌說明不可超過 500 字") String selfFundedNote) {

    public record Month(
            @Min(2000) @Max(2100) int year,
            @Min(1) @Max(12) int month,
            @Min(value = 0, message = "金額不可為負") Long cashIncome,
            @Min(value = 0, message = "金額不可為負") Long interest,
            @Min(value = 0, message = "金額不可為負") Long cashExpense,
            @Size(max = 300, message = "備註不可超過 300 字") String note) {
    }
}
