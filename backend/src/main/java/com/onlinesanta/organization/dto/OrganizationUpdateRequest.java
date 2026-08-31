package com.onlinesanta.organization.dto;

import com.onlinesanta.organization.ReleasePolicy;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 機構自行維護的資料。
 *
 * <p>審核狀態不在其中——那只能由平台管理員變更（M3）。
 */
public record OrganizationUpdateRequest(
        @NotBlank(message = "機構名稱不可為空")
        @Size(max = 120)
        String name,

        @NotBlank(message = "聯絡信箱不可為空")
        @Email(message = "聯絡信箱格式不正確")
        @Size(max = 255)
        String contactEmail,

        // 與註冊時一致。少了這兩條，機構之後在設定頁把電話或地址清空仍然存得了檔，
        // 而捐贈者的認領詳情頁正是靠它們顯示寄送地址
        @NotBlank(message = "聯絡電話不可為空")
        @Size(max = 40)
        String contactPhone,

        @NotBlank(message = "地址不可為空——捐贈者要靠它寄送禮物")
        @Size(max = 255)
        String address,

        @Size(max = 2000)
        String description,

        @NotNull(message = "請選擇逾期釋回政策")
        ReleasePolicy releasePolicy,

        /** 僅 releasePolicy = AUTO 時有意義；MANUAL 時會被忽略。 */
        @Min(value = 1, message = "寬限天數至少 1 天")
        @Max(value = 60, message = "寬限天數最多 60 天")
        Integer releaseAfterDays) {
}
