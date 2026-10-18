package com.onlinesanta.organization.dto;

import com.onlinesanta.organization.ReleasePolicy;

import com.onlinesanta.common.validation.LandlinePhone;
import com.onlinesanta.common.validation.MobilePhone;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
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

        @NotBlank(message = "承辦人姓名不可為空")
        @Size(max = 100)
        String contactPerson,

        @NotBlank(message = "聯絡信箱不可為空")
        @Email(message = "聯絡信箱格式不正確")
        @Size(max = 255)
        String contactEmail,

        // 跟註冊時一致：只有行動電話必填，聯絡電話選填，機構想清掉市話不會被擋。
        // 地址維持個別必填——少了它，捐贈者的認領詳情頁就顯示不出寄送地址
        @LandlinePhone
        @Size(max = 40)
        String contactPhone,

        @NotBlank(message = "行動電話不可為空")
        @MobilePhone
        @Size(max = 40, message = "行動電話不可超過 40 字")
        String mobilePhone,

        @NotBlank(message = "地址不可為空——捐贈者要靠它寄送禮物")
        @Size(max = 255)
        String address,

        @NotBlank(message = "方便收件時間不可為空")
        @Size(max = 255, message = "方便收件時間不可超過 255 字")
        String preferredDeliveryTime,

        @NotBlank(message = "機構簡介不可為空，讓捐贈者了解你們服務的對象")
        @Size(max = 100, message = "機構簡介不可超過 100 字")
        String description,

        @Size(max = 255, message = "官方網站不可超過 255 字")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "官方網站請填完整網址（https:// 開頭）")
        String website,

        @NotNull(message = "請選擇逾期釋回政策")
        ReleasePolicy releasePolicy,

        /** 僅 releasePolicy = AUTO 時有意義；MANUAL 時會被忽略。 */
        @Min(value = 1, message = "寬限天數至少 1 天")
        @Max(value = 60, message = "寬限天數最多 60 天")
        Integer releaseAfterDays) {

    /** 沒有官網的呼叫端（含測試）。 */
    public OrganizationUpdateRequest(String name, String contactPerson, String contactEmail,
                                     String contactPhone, String mobilePhone, String address,
                                     String preferredDeliveryTime, String description,
                                     ReleasePolicy releasePolicy, Integer releaseAfterDays) {
        this(name, contactPerson, contactEmail, contactPhone, mobilePhone, address,
                preferredDeliveryTime, description, null, releasePolicy, releaseAfterDays);
    }
}
