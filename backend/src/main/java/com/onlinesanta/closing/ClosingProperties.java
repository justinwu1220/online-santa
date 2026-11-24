package com.onlinesanta.closing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 結案文件設定（{@code app.closing.*}）。
 *
 * @param retentionDays 暫存在雲端的檔案（收據 PDF、匯出壓縮檔）保留幾天後自動清除；
 *                      清除不影響收據本身，開立當下的資料永久存資料庫，PDF 可重新產生且內容相同
 * @param purpose       收據上的「捐贈用途」句子（範本預印的那一句）
 * @param organizer     勸募團體全銜（結案文件抬頭、05A／B／C 的主辦與轉贈單位）
 * @param plannedAmount 勸募許可函上的預募金額（元）；照表操作頁的「預募金額」用它，系統本身沒有這筆資料
 */
@ConfigurationProperties(prefix = "app.closing")
public record ClosingProperties(
        @DefaultValue("30") int retentionDays,
        @DefaultValue("本活動服務對象之個人聖誕禮物") String purpose,
        @DefaultValue("社團法人彰化縣康乃心關懷協會") String organizer,
        @DefaultValue("500000") long plannedAmount) {
}
