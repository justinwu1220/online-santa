package com.onlinesanta.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 凍結願望：原因必填（至少 4 個字），會原封不動給機構看。 */
public record WishFreezeRequest(
        @NotBlank(message = "請說明凍結原因")
        @Size(min = 4, message = "凍結原因至少 4 個字")
        @Size(max = 1000, message = "凍結原因不可超過 1000 字")
        String note) {
}
