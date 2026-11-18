package com.onlinesanta.closing.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.onlinesanta.closing.CampaignStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增或修改活動檔期。
 *
 * @param permitNo         勸募許可文號；核准前可留空，留空時收據只存資料、不產生正式 PDF
 * @param receiptPrefix    收據編號前綴（大寫英數字），例如 OS2026；空白時新增會用 OS＋勸募開始年度
 * @param firstReceiptSeq  第一張收據的流水號；已開立過收據後不能再改
 * @param status           新增時忽略（一律進行中）；修改時可設為 CLOSED 結束檔期
 */
public record CampaignRequest(
        @NotBlank(message = "請填寫檔期名稱")
        @Size(max = 100, message = "檔期名稱不可超過 100 字")
        String name,

        @Size(max = 30, message = "活動編號不可超過 30 字")
        String activityNo,

        @Size(max = 100, message = "許可文號不可超過 100 字")
        String permitNo,

        @Size(max = 200, message = "核准活動全名不可超過 200 字")
        String approvedName,

        @NotNull(message = "請填寫勸募開始日") LocalDate collectionStart,
        @NotNull(message = "請填寫勸募結束日") LocalDate collectionEnd,
        @NotNull(message = "請填寫財物使用開始日") LocalDate useStart,
        @NotNull(message = "請填寫財物使用結束日") LocalDate useEnd,
        LocalDate cutoffDate,

        @Pattern(regexp = "^$|^[A-Z0-9]{2,20}$", message = "收據前綴只能是 2 到 20 個大寫英文字母或數字")
        String receiptPrefix,

        @Min(value = 1, message = "起始號碼至少是 1")
        @Max(value = 999_999, message = "起始號碼不可超過 999999")
        Integer firstReceiptSeq,

        UUID associationOrgId,

        CampaignStatus status) {
}
