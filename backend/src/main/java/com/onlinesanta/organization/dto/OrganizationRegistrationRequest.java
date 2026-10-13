package com.onlinesanta.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@AtLeastOnePhone
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

        // 電話從「單一必填」改成「兩種至少填一個」：小型機構常常只有承辦人的手機，
        // 大型機構只公布總機。單一欄位的 @NotBlank 會把合法的那一半擋掉，
        // 所以必填性上移到類別層級的 @AtLeastOnePhone，這裡只留長度上限。
        // 地址仍然是個別必填——捐贈者沒有它就寄不出去
        @Size(max = 40, message = "聯絡電話不可超過 40 字")
        String contactPhone,

        @Size(max = 40, message = "行動電話不可超過 40 字")
        String mobilePhone,

        @NotBlank(message = "地址不可為空——捐贈者要靠它寄送禮物")
        @Size(max = 255, message = "地址不可超過 255 字")
        String address,

        @NotBlank(message = "方便收件時間不可為空")
        @Size(max = 255, message = "方便收件時間不可超過 255 字")
        String preferredDeliveryTime,

        @Size(max = 2000, message = "機構簡介不可超過 2000 字")
        String description) implements AtLeastOnePhone.Phones {
}
