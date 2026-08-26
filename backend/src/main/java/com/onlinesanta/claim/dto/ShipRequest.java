package com.onlinesanta.claim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 捐贈者回報寄送資訊。 */
public record ShipRequest(
        @NotBlank(message = "請填寫物流業者")
        @Size(max = 60, message = "物流業者不可超過 60 字")
        String carrier,

        @NotBlank(message = "請填寫追蹤碼")
        @Size(max = 80, message = "追蹤碼不可超過 80 字")
        String trackingNumber) {
}
