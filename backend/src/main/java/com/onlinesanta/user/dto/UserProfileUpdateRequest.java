package com.onlinesanta.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 使用者在個人檔案頁自行維護的資料。 */
public record UserProfileUpdateRequest(
        @NotBlank(message = "用戶名稱不可為空")
        @Size(max = 100, message = "用戶名稱長度不可超過 100 字")
        String displayName,

        /** 真實姓名。選填：既有帳號與機構成員沒有這個資料，不該因此無法儲存其他欄位 */
        @Size(max = 100, message = "真實姓名長度不可超過 100 字")
        String name,

        @Size(max = 40, message = "連絡電話長度不可超過 40 字")
        String phone) {
}
