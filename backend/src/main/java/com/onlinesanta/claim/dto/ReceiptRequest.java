package com.onlinesanta.claim.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 捐贈收據資料，隨回報寄送一起送出。選擇不申請收據時整個物件為 null，所以這裡每個欄位都必填。
 *
 * <p>發票證明檔案不在這裡——先以 {@code RECEIPT_PROOF} 附件上傳，{@code ClaimService.ship}
 * 再確認至少有一個。
 */
public record ReceiptRequest(
        @NotBlank(message = "請填寫姓名或公司名稱")
        @Size(max = 100, message = "姓名或公司名稱不可超過 100 字")
        String name,

        /**
         * 個人填身分證字號（含外籍人士的居留證號：1 個英文字母＋1 個英文字母或數字＋8 碼數字），
         * 公司填統一編號（8 碼數字）。只驗證格式，不驗證檢查碼——這裡要擋的是明顯的誤填，
         * 真正的核對由管理員在開立收據時做。
         */
        @NotBlank(message = "請填寫身分證字號或統一編號")
        @Pattern(regexp = "(?i)^([A-Z][A-D0-9]\\d{8}|\\d{8})$",
                message = "格式不正確：身分證字號為 1 個英文字母加 9 碼數字，統一編號為 8 碼數字")
        String idNumber,

        @NotBlank(message = "請填寫電子信箱")
        @Email(message = "電子信箱格式不正確")
        @Size(max = 255, message = "電子信箱不可超過 255 字")
        String email,

        @NotBlank(message = "請填寫收件地址")
        @Size(max = 255, message = "收件地址不可超過 255 字")
        String address) {
}
