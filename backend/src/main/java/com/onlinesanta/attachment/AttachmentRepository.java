package com.onlinesanta.attachment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

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
