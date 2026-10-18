package com.onlinesanta.organization.dto;

import com.onlinesanta.common.validation.LandlinePhone;
import com.onlinesanta.common.validation.MobilePhone;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationRegistrationRequest(
        @NotBlank(message = "機構名稱不可為空")
        @Size(max = 120, message = "機構名稱不可超過 120 字")
        String name,

        @NotBlank(message = "承辦人姓名不可為空")
        @Size(max = 100, message = "承辦人姓名不可超過 100 字")
        String contactPerson,

        @NotBlank(message = "聯絡信箱不可為空")
        @Email(message = "聯絡信箱格式不正確")
        @Size(max = 255)
        String contactEmail,

        // 只有行動電話必填：承辦人的手機是平台與捐贈者最穩定的聯絡方式，
        // 聯絡電話（市話）選填。地址同樣個別必填——捐贈者沒有它就寄不出去
        @LandlinePhone
        @Size(max = 40, message = "聯絡電話不可超過 40 字")
        String contactPhone,

        @NotBlank(message = "行動電話不可為空")
        @MobilePhone
        @Size(max = 40, message = "行動電話不可超過 40 字")
        String mobilePhone,

        @NotBlank(message = "地址不可為空——捐贈者要靠它寄送禮物")
        @Size(max = 255, message = "地址不可超過 255 字")
        String address,

        @NotBlank(message = "方便收件時間不可為空")
        @Size(max = 255, message = "方便收件時間不可超過 255 字")
        String preferredDeliveryTime,

        @NotBlank(message = "機構簡介不可為空，讓捐贈者了解你們服務的對象")
        @Size(max = 100, message = "機構簡介不可超過 100 字")
        String description,

        /** 選填；須為完整網址（http:// 或 https:// 開頭）。 */
        @Size(max = 255, message = "官方網站不可超過 255 字")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "官方網站請填完整網址（https:// 開頭）")
        String website) {

    /** 沒有官網的呼叫端（含測試）。 */
    public OrganizationRegistrationRequest(String name, String contactPerson, String contactEmail,
                                           String contactPhone, String mobilePhone, String address,
                                           String preferredDeliveryTime, String description) {
        this(name, contactPerson, contactEmail, contactPhone, mobilePhone, address,
                preferredDeliveryTime, description, null);
    }
}
