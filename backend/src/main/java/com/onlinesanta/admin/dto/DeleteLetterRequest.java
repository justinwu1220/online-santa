package com.onlinesanta.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理員刪除願望的信件照片：原因必填，機構會在對話看到。 */
public record DeleteLetterRequest(
        @NotBlank(message = "請填寫刪除原因")
        @Size(min = 4, max = 500, message = "刪除原因至少 4 個字，最多 500 字")
        String reason) {
}
