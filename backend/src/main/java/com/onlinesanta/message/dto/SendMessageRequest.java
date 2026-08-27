package com.onlinesanta.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "訊息內容不可為空")
        @Size(max = 2000, message = "訊息不可超過 2000 字")
        String body) {
}
