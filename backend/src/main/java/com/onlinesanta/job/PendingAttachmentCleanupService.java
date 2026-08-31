package com.onlinesanta.job;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.onlinesanta.attachment.Attachment;
import com.onlinesanta.attachment.AttachmentRepository;
import com.onlinesanta.attachment.AttachmentService;
import com.onlinesanta.attachment.UploadStatus;
import com.onlinesanta.job.dto.AttachmentCleanupResult;

/**
 * 放棄的上傳留下的孤兒附件清理。
 *
 * <p>三步驟直傳流程（索取簽章網址 → 前端直傳 → 回頭確認）中間任何一步可能被放棄：
 * 使用者拿到網址後關掉分頁、傳到一半斷線、或傳完了但沒有觸發確認。這些都會留下
 * 永久的 PENDING 列，可能還連著一個真的傳上去、但從沒被任何人看過的 GCS 物件。
 *
 * <p><strong>逐筆處理，單筆失敗不擋整批。</strong>這點與 {@link ClaimReleaseService}
 * 刻意不同：那裡「一天最多幾百筆、要嘛全部釋回要嘛全部不動」比較好判斷，這裡則是
 * 在清垃圾——清十筆裡的九筆已經達成大部分目的，不該因為第十筆的儲存端偶發錯誤，
 * 讓前九筆也一起回滾。因此每一筆的刪除都是獨立的交易（見
 * {@link AttachmentService#deletePendingAttachment}），不是包在同一個
 * {@code @Transactional} 方法裡。
 */
@Service
public class PendingAttachmentCleanupService {

    private static final Logger log = LoggerFactory.getLogger(PendingAttachmentCleanupService.class);

    /** 超過這個時間仍是 PENDING，視為放棄的上傳。 */
    static final Duration PENDING_TTL = Duration.ofHours(24);

    private final AttachmentRepository attachments;
    private final AttachmentService attachmentService;

    public PendingAttachmentCleanupService(AttachmentRepository attachments,
                                           AttachmentService attachmentService) {
        this.attachments = attachments;
        this.attachmentService = attachmentService;
    }

    public AttachmentCleanupResult cleanup() {
        Instant cutoff = Instant.now().minus(PENDING_TTL);
        List<Attachment> stale = attachments.findByUploadStatusAndCreatedAtBefore(
                UploadStatus.PENDING, cutoff);

        int deleted = 0;
        int failed = 0;
        for (Attachment attachment : stale) {
            try {
                attachmentService.deletePendingAttachment(attachment.getId());
                deleted++;
            } catch (Exception e) {
                failed++;
                log.warn("清理 PENDING 附件失敗，略過繼續下一筆：id={}, purpose={}, objectName={}",
                        attachment.getId(), attachment.getPurpose(), attachment.getObjectName(), e);
            }
        }

        AttachmentCleanupResult result = new AttachmentCleanupResult(cutoff, stale.size(), deleted, failed);
        if (result.hasWork()) {
            log.info("PENDING 附件清理：掃到 {} 筆，成功清除 {} 筆，失敗 {} 筆",
                    result.found(), result.deleted(), result.failed());
        } else {
            log.debug("PENDING 附件清理：沒有超過 24 小時的孤兒附件");
        }
        return result;
    }
}
