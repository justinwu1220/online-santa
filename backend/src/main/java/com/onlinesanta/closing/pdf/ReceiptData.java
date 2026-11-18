package com.onlinesanta.closing.pdf;

import java.time.LocalDate;

/**
 * 一張收據要印的內容（已是最終文字，渲染器不再做任何資料查詢或判斷）。
 *
 * @param donorIdNumber    身分證字號／統一編號；沒有（未勾選要收據的捐贈者）就留空白，不填假證號
 * @param permitNo         勸募許可文號；渲染前必須已有值，沒有就不應該產生正式 PDF
 * @param purpose          捐贈用途（範本預印的句子）
 * @param valueYuan        該張收據的物資總時價（新臺幣元），0 到 9,999,999
 * @param recipientOrgName 實際收受機構
 */
public record ReceiptData(
        String receiptNo,
        LocalDate issuedOn,
        String donorName,
        String donorIdNumber,
        LocalDate donatedOn,
        String activityName,
        String permitNo,
        LocalDate collectionStart,
        LocalDate collectionEnd,
        String purpose,
        String itemDescription,
        int quantity,
        String unit,
        int valueYuan,
        String recipientOrgName) {
}
