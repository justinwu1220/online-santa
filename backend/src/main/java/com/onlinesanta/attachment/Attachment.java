package com.onlinesanta.attachment;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.common.BaseEntity;
import com.onlinesanta.common.exception.BusinessRuleException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Cloud Storage 上一個物件的中繼資料。
 *
 * <p>檔案本身由前端透過 Signed URL 直傳，不經過本服務；這張表只記錄它在哪裡、
 * 屬於誰、以及是否已確認上傳成功。
 */
@Entity
@Table(name = "attachments")
public class Attachment extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, updatable = false, length = 30)
    private AttachmentPurpose purpose;

    /** 依 purpose 而定：WISH_IMAGE 指向願望，其餘指向認領。 */
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "object_name", nullable = false, updatable = false, length = 500)
    private String objectName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /**
     * 使用者上傳時的原始檔名，純粹給人看（監控中心／機構後台顯示用），不影響
     * 儲存或授權邏輯——{@link #objectName} 才是實際存取用的鍵。可能是 null：
     * 這支功能上線前就存在的舊附件沒有這項資訊。
     */
    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    private UploadStatus uploadStatus;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected Attachment() {
        // JPA
    }

    private Attachment(AttachmentPurpose purpose, UUID ownerId, String objectName,
                       String contentType, UUID uploadedBy, String originalFilename) {
        this.purpose = purpose;
        this.ownerId = ownerId;
        this.objectName = objectName;
        this.contentType = contentType;
        this.uploadedBy = uploadedBy;
        this.originalFilename = originalFilename;
        this.uploadStatus = UploadStatus.PENDING;
    }

    /**
     * 發出上傳網址的同時建立紀錄，此時檔案還不存在。
     *
     * @param originalFilename 使用者端的原始檔名，選填（可能是 null）——純粹顯示用
     */
    public static Attachment pending(AttachmentPurpose purpose, UUID ownerId, String objectName,
                                     String contentType, UUID uploadedBy, String originalFilename) {
        return new Attachment(purpose, ownerId, objectName, contentType, uploadedBy, originalFilename);
    }

    /**
     * 確認上傳完成。
     *
     * <p>參數來自儲存端的實際查詢結果，而非前端宣稱的值——前端可以宣稱它傳了一張
     * 100KB 的 JPEG，實際上傳的是別的東西。
     */
    public void confirm(String actualContentType, long actualSizeBytes) {
        if (uploadStatus == UploadStatus.CONFIRMED) {
            throw new BusinessRuleException("ATTACHMENT_ALREADY_CONFIRMED", "這個附件已經確認過了");
        }
        this.contentType = actualContentType;
        this.sizeBytes = actualSizeBytes;
        this.uploadStatus = UploadStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }

    public boolean isConfirmed() {
        return uploadStatus == UploadStatus.CONFIRMED;
    }

    public boolean wasUploadedBy(UUID userId) {
        return uploadedBy != null && uploadedBy.equals(userId);
    }

    public AttachmentPurpose getPurpose() {
        return purpose;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public UploadStatus getUploadStatus() {
        return uploadStatus;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
