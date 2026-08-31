package com.onlinesanta.attachment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * 放棄的上傳：拿到簽章網址但沒有真的傳完、或傳完了卻沒有回頭 confirm 的附件。
     * 供 PENDING 附件清理排程使用。
     */
    List<Attachment> findByUploadStatusAndCreatedAtBefore(UploadStatus uploadStatus, Instant cutoff);

    /** 對外查詢一律只看已確認的：PENDING 的紀錄對應的檔案可能根本不存在。 */
    List<Attachment> findByPurposeAndOwnerIdAndUploadStatusOrderByCreatedAtAsc(
            AttachmentPurpose purpose, UUID ownerId, UploadStatus uploadStatus);

    List<Attachment> findByPurposeInAndOwnerIdAndUploadStatusOrderByCreatedAtAsc(
            Collection<AttachmentPurpose> purposes, UUID ownerId, UploadStatus uploadStatus);

    long countByPurposeAndOwnerIdAndUploadStatus(
            AttachmentPurpose purpose, UUID ownerId, UploadStatus uploadStatus);

    Optional<Attachment> findFirstByPurposeAndOwnerIdAndUploadStatusOrderByConfirmedAtDesc(
            AttachmentPurpose purpose, UUID ownerId, UploadStatus uploadStatus);

    /** 一次撈多個願望的示意圖，避免願望牆逐筆查詢。 */
    List<Attachment> findByPurposeAndOwnerIdInAndUploadStatus(
            AttachmentPurpose purpose, Collection<UUID> ownerIds, UploadStatus uploadStatus);
}
