package com.onlinesanta.closing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 收據開立（願望完成的交易提交）之後自動產生 PDF：產生失敗或太慢都不該影響願望完成。
 *
 * <p><strong>必須開新交易。</strong>AFTER_COMMIT 階段原本的交易雖然已經提交，但交易資源還掛在執行緒上，
 * 沿用它（預設的 REQUIRED）寫入的資料不會被提交——PDF 檔案傳上去了，READY 狀態卻沒寫進資料庫，
 * 收據會一直停在「待產生」。所以用 REQUIRES_NEW 的交易樣板執行，並自己接住所有例外
 * （AFTER_COMMIT 的例外會往上拋到呼叫願望完成的請求）。
 *
 * <p>失敗時收據的 PDF 狀態維持「待產生」，預覽或匯出時會自動補產。
 */
@Component
public class DonationReceiptPdfListener {

    private static final Logger log = LoggerFactory.getLogger(DonationReceiptPdfListener.class);

    private final DonationReceiptService receipts;
    private final TransactionTemplate newTransaction;

    public DonationReceiptPdfListener(DonationReceiptService receipts, PlatformTransactionManager transactionManager) {
        this.receipts = receipts;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssued(DonationReceiptIssuedEvent event) {
        try {
            newTransaction.executeWithoutResult(status -> receipts.generatePdf(event.receiptId()));
        } catch (RuntimeException e) {
            log.error("產生收據 PDF 失敗，之後預覽或匯出時會再試。receiptId={}", event.receiptId(), e);
        }
    }
}
