package com.onlinesanta.organization.dto;

import jakarta.validation.constraints.Email;
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

        // 電話與地址是必填：捐贈者認領後要靠它們把禮物寄到，而管理員審核時
        // 也需要它們才判斷得出這是不是一個真的機構
        @NotBlank(message = "聯絡電話不可為空")
        @Size(max = 40, message = "聯絡電話不可超過 40 字")
        String contactPhone,

        @NotBlank(message = "地址不可為空——捐贈者要靠它寄送禮物")
        @Size(max = 255, message = "地址不可超過 255 字")
        String address,

        @Size(max = 2000, message = "機構簡介不可超過 2000 字")
        String description) {
}
