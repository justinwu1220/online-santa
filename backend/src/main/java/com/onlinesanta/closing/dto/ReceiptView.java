package com.onlinesanta.closing.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.onlinesanta.closing.DonationReceipt;
import com.onlinesanta.closing.PdfStatus;
import com.onlinesanta.closing.ReceiptStatus;

/**
 * 收據清單的一列。<strong>不含身分證字號</strong>：清單用來瀏覽與處理，證號只出現在收據 PDF 裡
 * （下載 PDF 會寫稽核）。
 */
public record ReceiptView(
        UUID id,
        UUID claimId,
        String receiptNo,
        ReceiptStatus status,
        PdfStatus pdfStatus,
        String pdfError,
        String donorName,
        boolean receiptRequested,
        String donorEmail,
        String giftDescription,
        int giftQuantity,
        String giftUnit,
        int giftValue,
        LocalDate donatedOn,
        LocalDate issuedOn,
        String organizationName,
        String voidReason,
        Instant voidedAt,
        UUID replacedById,
        Instant redSentAt,
        String redSendError) {

    public static ReceiptView from(DonationReceipt r) {
        return new ReceiptView(r.getId(), r.getClaimId(), r.getReceiptNo(), r.getStatus(), r.getPdfStatus(),
                r.getPdfError(), r.getDonorName(), r.isReceiptRequested(), r.getDonorEmail(),
                r.getGiftDescription(), r.getGiftQuantity(), r.getGiftUnit(), r.getGiftValue(), r.getDonatedOn(),
                r.getIssuedOn(), r.getOrganizationName(), r.getVoidReason(), r.getVoidedAt(), r.getReplacedById(),
                r.getRedSentAt(), r.getRedSendError());
    }
}
