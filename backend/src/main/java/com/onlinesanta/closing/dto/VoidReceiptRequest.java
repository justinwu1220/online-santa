package com.onlinesanta.closing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 作廢並重開收據：原因必填，會留在舊收據上。 */
public record VoidReceiptRequest(
        @NotBlank(message = "請填寫作廢原因")
        @Size(min = 2, max = 500, message = "作廢原因至少 2 個字，最多 500 字")
        String reason) {
}
