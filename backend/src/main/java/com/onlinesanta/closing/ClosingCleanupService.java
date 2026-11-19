package com.onlinesanta.closing;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.onlinesanta.job.dto.ClosingCleanupResult;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageBucket;

/**
 * 結案文件暫存檔的到期清除（預設 30 天）。
 *
 * <p>只清雲端檔案，資料庫裡的收據資料永久保留：收據的 PDF 過期後狀態回到「待產生」，下次需要時依快照重新產生，
 * 內容相同；匯出壓縮檔過期後標成 EXPIRED，要重新匯出。
 *
 * <p>逐筆處理、各自一個交易，單筆失敗不擋整批（理由同 {@code PendingAttachmentCleanupService}）。
 * 先刪檔案再改資料庫：刪檔失敗就不改狀態，下次再試；刪檔成功但改資料庫失敗，下次重刪不存在的檔案無害。
 */
@Service
public class ClosingCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ClosingCleanupService.class);

    private final DonationReceiptRepository receipts;
    private final ClosingExportRepository exports;
    private final ObjectStorage storage;
    private final TransactionTemplate tx;

    public ClosingCleanupService(DonationReceiptRepository receipts, ClosingExportRepository exports,
                                 ObjectStorage storage, PlatformTransactionManager transactionManager) {
        this.receipts = receipts;
        this.exports = exports;
        this.storage = storage;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public ClosingCleanupResult cleanup() {
        Instant now = Instant.now();
        List<java.util.UUID> receiptIds = tx.execute(status -> receipts
                .findByPdfStatusAndPdfExpiresAtBefore(PdfStatus.READY, now).stream().map(DonationReceipt::getId).toList());
        List<java.util.UUID> exportIds = tx.execute(status -> exports
                .findByStatusAndExpiresAtBefore(ExportStatus.READY, now).stream().map(ClosingExport::getId).toList());

        int receiptsCleared = 0;
        int exportsCleared = 0;
        int failed = 0;
        for (java.util.UUID id : receiptIds) {
            try {
                tx.executeWithoutResult(status -> receipts.findById(id).ifPresent(r -> {
                    deleteQuietly(r.getRedObject());
                    deleteQuietly(r.getWhiteYellowObject());
                    r.markPdfExpired();
                    receipts.save(r);
                }));
                receiptsCleared++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("清除收據 PDF 暫存失敗：receiptId={}", id, e);
            }
        }
        for (java.util.UUID id : exportIds) {
            try {
                tx.executeWithoutResult(status -> exports.findById(id).ifPresent(export -> {
                    deleteQuietly(export.getObjectName());
                    export.expire();
                    exports.save(export);
                }));
                exportsCleared++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("清除匯出壓縮檔失敗：exportId={}", id, e);
            }
        }
        log.info("結案文件暫存清除：收據 {}/{}、匯出 {}/{}、失敗 {}",
                receiptsCleared, receiptIds.size(), exportsCleared, exportIds.size(), failed);
        return new ClosingCleanupResult(now, receiptIds.size(), receiptsCleared, exportIds.size(), exportsCleared, failed);
    }

    /** 物件已不存在視為成功；真正的儲存錯誤會往外丟，讓呼叫端記為失敗並保留狀態。 */
    private void deleteQuietly(String objectName) {
        if (objectName != null) {
            storage.delete(StorageBucket.PRIVATE, objectName);
        }
    }
}
