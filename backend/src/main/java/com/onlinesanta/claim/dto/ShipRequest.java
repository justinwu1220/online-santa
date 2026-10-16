package com.onlinesanta.claim.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** 捐贈者回報寄送資訊。禮物資訊供機構收貨時核對，全部必填。 */
public record ShipRequest(
        @NotBlank(message = "請填寫物流業者")
        @Size(max = 60, message = "物流業者不可超過 60 字")
        String carrier,

        @NotBlank(message = "請填寫追蹤碼")
        @Size(max = 80, message = "追蹤碼不可超過 80 字")
        String trackingNumber,

        @NotBlank(message = "請填寫實際禮物內容")
        @Size(max = 200, message = "禮物內容不可超過 200 字")
        String giftDescription,

        /** 份數，單位固定為「份」 */
        @NotNull(message = "請填寫數量")
        @Min(value = 1, message = "數量至少 1 份")
        @Max(value = 9999, message = "數量不可超過 9999 份")
        Integer giftQuantity,

        /** 新台幣整數 */
        @NotNull(message = "請填寫禮物價格")
        @Min(value = 0, message = "禮物價格不可為負")
        @Max(value = 10_000_000, message = "禮物價格不可超過 10,000,000 元")
        Integer giftPrice,

        @NotNull(message = "請選擇寄出日期")
        @PastOrPresent(message = "寄出日期不可是未來的日期")
        LocalDate shippedOn) {
}
