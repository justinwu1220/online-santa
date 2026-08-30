package com.onlinesanta.attachment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.attachment.dto.UploadUrlResponse;
import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ForbiddenException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StoredObject;
import com.onlinesanta.storage.StorageProperties;
import com.onlinesanta.storage.UploadTarget;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishRepository;

/**
 * 附件的授權、上傳網址發放與上傳確認。
 *
 * <p>三種用途各有不同的「誰能上傳」規則，全部集中在 {@link #authorizeUpload}——
 * 分散在各個端點裡遲早會漏掉一個。
 */
@Service
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final WishRepository wishes;
    private final ClaimRepository claims;
    private final ObjectStorage storage;
    private final StorageProperties properties;
    private final CurrentUserService currentUser;

    public AttachmentService(AttachmentRepository attachments,
                             WishRepository wishes,
                             ClaimRepository claims,
                             ObjectStorage storage,
                             StorageProperties properties,
                             CurrentUserService currentUser) {
        this.attachments = attachments;
        this.wishes = wishes;
        this.claims = claims;
        this.storage = storage;
        this.properties = properties;
        this.currentUser = currentUser;
    }

    // ================================================================ 上傳

    @Transactional
    public UploadUrlResponse createUploadUrl(UploadUrlRequest request) {
        AppPrincipal principal = currentUser.require();

        requirePurposeEnabled(request.purpose());
        requireAllowedContentType(request.contentType());
        requireWithinSizeLimit(request.sizeBytes());
        authorizeUpload(request.purpose(), request.targetId(), principal);
        requireWithinCountLimit(request.purpose(), request.targetId());

        String objectName = "%s/%s/%s.%s".formatted(
                request.purpose().prefix(),
                request.targetId(),
                UUID.randomUUID(),
                properties.extensionFor(request.contentType()));

        UploadTarget target = storage.createUploadUrl(
                request.purpose().bucket(), objectName,
                request.contentType(), properties.uploadUrlTtl());

        Attachment attachment = attachments.save(Attachment.pending(
                request.purpose(), request.targetId(), objectName,
                request.contentType(), principal.userId()));

        return new UploadUrlResponse(
                attachment.getId(), target.url(), target.contentType(), target.expiresAt());
    }

    /**
     * 確認上傳完成。
     *
     * <p>不採信前端的說法：向儲存端實際查詢物件，核對型別與大小。前端大可宣稱自己
     * 傳了一張小圖，實際放上去的是別的東西——真正決定內容的是儲存端，所以要問它。
     */
    @Transactional
    public AttachmentView confirm(UUID attachmentId) {
        AppPrincipal principal = currentUser.require();
        Attachment attachment = attachments.findById(attachmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("附件", attachmentId));

        if (!attachment.wasUploadedBy(principal.userId())) {
            throw ResourceNotFoundException.of("附件", attachmentId);
        }

        StoredObject stored = storage
                .find(attachment.getPurpose().bucket(), attachment.getObjectName())
                .orElseThrow(() -> new BusinessRuleException(
                        "UPLOAD_NOT_FOUND", "找不到上傳的檔案，請重新上傳"));

        requireAllowedContentType(stored.contentType());
        requireWithinSizeLimit(stored.sizeBytes());

        attachment.confirm(stored.contentType(), stored.sizeBytes());

        // 願望示意圖只保留最新一張，舊的連同檔案一起汰除
        if (attachment.getPurpose().replacesPrevious()) {
            removePreviousVersions(attachment);
        }

        return toView(attachment);
    }

    private void removePreviousVersions(Attachment current) {
        attachments.findByPurposeAndOwnerIdAndUploadStatusOrderByCreatedAtAsc(
                        current.getPurpose(), current.getOwnerId(), UploadStatus.CONFIRMED)
                .stream()
                .filter(existing -> !existing.getId().equals(current.getId()))
                .forEach(stale -> {
                    storage.delete(stale.getPurpose().bucket(), stale.getObjectName());
                    attachments.delete(stale);
                });
    }

    // ================================================================ 讀取

    /** 願望的示意圖網址。公開 bucket，固定網址，不需簽章。 */
    @Transactional(readOnly = true)
    public Map<UUID, String> wishImageUrls(Collection<UUID> wishIds) {
        if (wishIds.isEmpty()) {
            return Map.of();
        }
        return attachments.findByPurposeAndOwnerIdInAndUploadStatus(
                        AttachmentPurpose.WISH_IMAGE, wishIds, UploadStatus.CONFIRMED)
                .stream()
                .collect(Collectors.toMap(
                        Attachment::getOwnerId,
                        attachment -> storage.publicUrl(attachment.getObjectName()),
                        // 同一願望理論上只有一張，真有重複時取先建立的那張
                        (first, second) -> first));
    }

    @Transactional(readOnly = true)
    public String wishImageUrl(UUID wishId) {
        return attachments.findFirstByPurposeAndOwnerIdAndUploadStatusOrderByConfirmedAtDesc(
                        AttachmentPurpose.WISH_IMAGE, wishId, UploadStatus.CONFIRMED)
                .map(attachment -> storage.publicUrl(attachment.getObjectName()))
                .orElse(null);
    }

    /**
     * 某筆認領的全部附件（寄送證明與回饋照片）。
     *
     * <p>呼叫端必須先做過權限檢查——這些檔案含捐贈者個資與孩童影像，只有該筆認領的
     * 捐贈者與願望所屬機構能看。
     */
    @Transactional(readOnly = true)
    public List<AttachmentView> claimAttachments(UUID claimId) {
        return attachments.findByPurposeInAndOwnerIdAndUploadStatusOrderByCreatedAtAsc(
                        List.of(AttachmentPurpose.SHIPPING_PROOF, AttachmentPurpose.ORG_FEEDBACK),
                        claimId, UploadStatus.CONFIRMED)
                .stream()
                .map(this::toView)
                .toList();
    }

    private AttachmentView toView(Attachment attachment) {
        String url = attachment.getPurpose().isPubliclyReadable()
                ? storage.publicUrl(attachment.getObjectName())
                : storage.createDownloadUrl(attachment.getPurpose().bucket(),
                        attachment.getObjectName(), properties.downloadUrlTtl());

        return new AttachmentView(
                attachment.getId(),
                attachment.getPurpose(),
                url,
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getConfirmedAt());
    }

    // ================================================================ 授權

    /**
     * 誰能上傳什麼。
     *
     * <p>除了身分之外也檢查流程狀態：已完成的認領不該還能補上寄送證明，
     * 尚未收到禮物的認領也不該有「送禮回饋照片」。
     */
    private void authorizeUpload(AttachmentPurpose purpose, UUID targetId, AppPrincipal principal) {
        switch (purpose) {
            case WISH_IMAGE -> authorizeWishImage(targetId, principal);
            case SHIPPING_PROOF -> authorizeShippingProof(targetId, principal);
            case ORG_FEEDBACK -> authorizeOrganizationFeedback(targetId, principal);
        }
    }

    private void authorizeWishImage(UUID wishId, AppPrincipal principal) {
        UUID organizationId = currentUser.requireOrganizationId();
        Wish wish = wishes.findWithOrganizationById(wishId)
                .orElseThrow(() -> ResourceNotFoundException.of("願望", wishId));

        // 不屬於自己的願望一律回 404，不透露它是否存在
        if (!wish.getOrganization().getId().equals(organizationId)) {
            throw ResourceNotFoundException.of("願望", wishId);
        }
        if (!wish.getStatus().isEditable()) {
            throw new BusinessRuleException("WISH_NOT_EDITABLE",
                    "願望已進入認領流程，無法更換示意圖，目前狀態為 " + wish.getStatus());
        }
    }

    private void authorizeShippingProof(UUID claimId, AppPrincipal principal) {
        Claim claim = findClaim(claimId);
        if (!claim.isOwnedBy(principal.userId())) {
            throw ResourceNotFoundException.of("認領", claimId);
        }
        requireStatusIn(claim, "上傳寄送證明", ClaimStatus.CLAIMED, ClaimStatus.SHIPPED);
    }

    private void authorizeOrganizationFeedback(UUID claimId, AppPrincipal principal) {
        UUID organizationId = currentUser.requireOrganizationId();
        Claim claim = findClaim(claimId);

        if (!claim.getWish().getOrganization().getId().equals(organizationId)) {
            throw ResourceNotFoundException.of("認領", claimId);
        }
        requireStatusIn(claim, "上傳送禮回饋", ClaimStatus.RECEIVED, ClaimStatus.COMPLETED);
    }

    private Claim findClaim(UUID claimId) {
        return claims.findWithDetailsById(claimId)
                .orElseThrow(() -> ResourceNotFoundException.of("認領", claimId));
    }

    private void requireStatusIn(Claim claim, String action, ClaimStatus... allowed) {
        for (ClaimStatus status : allowed) {
            if (claim.getStatus() == status) {
                return;
            }
        }
        throw new BusinessRuleException("CLAIM_STATE_NOT_ALLOWED",
                "認領目前是 %s，不能%s".formatted(claim.getStatus(), action));
    }

    // ================================================================ 檔案限制

    /**
     * 願望示意圖可以整個關閉，關閉時公開 bucket 不需要存在。
     *
     * <p>前端會一併隱藏上傳按鈕，但那只是介面。少了這道檢查，任何人都能直接呼叫
     * 這個端點，把檔案寫進一個沒有人在看管、甚至還沒建立的 bucket。
     */
    private void requirePurposeEnabled(AttachmentPurpose purpose) {
        if (purpose == AttachmentPurpose.WISH_IMAGE && !properties.wishImageEnabled()) {
            throw new ForbiddenException("WISH_IMAGE_DISABLED",
                    "目前不開放上傳願望示意圖");
        }
    }

    private void requireAllowedContentType(String contentType) {
        if (!properties.allows(contentType)) {
            throw new BusinessRuleException("UNSUPPORTED_CONTENT_TYPE",
                    "只接受 %s；不支援 SVG 等可夾帶腳本的格式"
                            .formatted(String.join("、", properties.allowedContentTypes())));
        }
    }

    private void requireWithinSizeLimit(long sizeBytes) {
        if (sizeBytes > properties.maxUploadBytes()) {
            throw new BusinessRuleException("FILE_TOO_LARGE",
                    "檔案不可超過 %d MB".formatted(properties.maxUploadBytes() / (1024 * 1024)));
        }
    }

    /**
     * 數量上限只算已確認的附件。
     *
     * <p>沒完成的 PENDING 紀錄不佔額度，否則使用者上傳失敗幾次之後就再也傳不了。
     */
    private void requireWithinCountLimit(AttachmentPurpose purpose, UUID ownerId) {
        if (purpose.replacesPrevious()) {
            return;  // 只保留最新一張，不需要限制數量
        }
        long confirmed = attachments.countByPurposeAndOwnerIdAndUploadStatus(
                purpose, ownerId, UploadStatus.CONFIRMED);
        if (confirmed >= purpose.maxPerOwner()) {
            throw new BusinessRuleException("ATTACHMENT_LIMIT_REACHED",
                    "最多只能上傳 %d 個檔案".formatted(purpose.maxPerOwner()));
        }
    }
}
