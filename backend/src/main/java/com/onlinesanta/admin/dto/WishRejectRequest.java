package com.onlinesanta.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 退回願望：原因必填（至少 5 個字），會原封不動給機構看。 */
public record WishRejectRequest(
        @NotBlank(message = "請說明退回原因")
        @Size(min = 5, message = "退回原因至少 5 個字")
        @Size(max = 1000, message = "退回原因不可超過 1000 字")
        String note) {
}
