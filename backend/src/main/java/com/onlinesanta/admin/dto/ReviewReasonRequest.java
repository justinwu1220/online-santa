package com.onlinesanta.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 需要說明理由的審核決定：退件與停權。
 *
 * <p>與 {@link ReviewDecisionRequest}（核准/復權用，附註選填）分開成獨立型別，
 * 而非共用同一個 record 加驗證分組——分組要求呼叫端知道該用哪個 group 才看得懂
 * 為什麼同一個欄位在不同端點有不同的必填規則；分開型別讓「這個決定一定要說明
 * 理由」直接寫在方法簽章上，一看就懂，也不必在這個專案裡第一次引入驗證分組。
 */
public record ReviewReasonRequest(
        @NotBlank(message = "請說明理由")
        @Size(max = 1000, message = "理由不可超過 1000 字")
        String note) {
}
