package com.onlinesanta.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationRegistrationRequest(
        @NotBlank(message = "機構名稱不可為空")
        @Size(max = 120, message = "機構名稱不可超過 120 字")
        String name,

        @NotBlank(message = "聯絡信箱不可為空")
        @Email(message = "聯絡信箱格式不正確")
        @Size(max = 255)
        String contactEmail,

        @Size(max = 40, message = "聯絡電話不可超過 40 字")
        String contactPhone,

        @Size(max = 255, message = "地址不可超過 255 字")
        String address,

        @Size(max = 2000, message = "機構簡介不可超過 2000 字")
        String description) {
}
