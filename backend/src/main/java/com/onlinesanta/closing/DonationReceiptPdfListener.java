package com.onlinesanta.closing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 收據開立（願望完成的交易提交）之後才產生 PDF：產生失敗或太慢都不該影響願望完成。
 * 失敗時收據的 PDF 狀態維持「待產生」，匯出或下載時會補產。
 */
@Component
public class DonationReceiptPdfListener {

    private static final Logger log = LoggerFactory.getLogger(DonationReceiptPdfListener.class);

    private final DonationReceiptService receipts;

    public DonationReceiptPdfListener(DonationReceiptService receipts) {
        this.receipts = receipts;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssued(DonationReceiptIssuedEvent event) {
        try {
            receipts.generatePdf(event.receiptId());
        } catch (RuntimeException e) {
            log.error("產生收據 PDF 失敗，之後匯出或下載時會再試。receiptId={}", event.receiptId(), e);
        }
    }
}
