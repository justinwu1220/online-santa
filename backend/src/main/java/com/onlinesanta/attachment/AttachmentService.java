package com.onlinesanta.attachment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
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
import com.onlinesanta.event.FeedbackPhotoConfirmedEvent;
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
    private final ApplicationEventPublisher eventPublisher;

    public AttachmentService(AttachmentRepository attachments,
                             WishRepository wishes,
                             ClaimRepository claims,
                             ObjectStorage storage,
                             StorageProperties properties,
                             CurrentUserService currentUser,
                             ApplicationEventPublisher eventPublisher) {
        this.attachments = attachments;
        this.wishes = wishes;
        this.claims = claims;
        this.storage = storage;
        this.properties = properties;
        this.currentUser = currentUser;
        this.eventPublisher = eventPublisher;
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

        // 回饋照片確認上傳成功，通知捐贈者——ownerId 就是 claimId（見 AttachmentPurpose）
        if (attachment.getPurpose() == AttachmentPurpose.ORG_FEEDBACK) {
            eventPublisher.publishEvent(new FeedbackPhotoConfirmedEvent(attachment.getOwnerId()));
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

    // ================================================================ 刪除

    /**
     * 刪除一個附件。範圍限 {@link AttachmentPurpose#SHIPPING_PROOF} 與
     * {@link AttachmentPurpose#ORG_FEEDBACK}——{@link AttachmentPurpose#WISH_IMAGE}
     * 有「換新即汰舊」的既有語意（見 {@link #confirm}），不透過這支端點刪除。
     *
     * <p>三種呼叫者共用同一個端點：SHIPPING_PROOF 限該筆認領的捐贈者本人、
     * ORG_FEEDBACK 限願望所屬機構，平台管理員兩種都能刪（隱私事件處置用，例如誤傳
     * 的孩童照片）。管理員的呼叫由 {@code AttachmentController} 負責寫稽核紀錄——
     * 稽核是「誰、為何能繞過一般權限」這件事的紀錄，不屬於這裡的業務邏輯。
     *
     * <p><strong>terminal 狀態的認領也允許刪。</strong>上傳有狀態限制（例如認領已
     * COMPLETED 就不能再補寄送證明），但刪除沒有：一張照片就算認領流程已經走完，
     * 只要有人要求下架，隱私考量一律優先於「認領紀錄要不要保持完整封存」。
     *
     * <p>刪除順序刻意是「先刪儲存物件，成功後才刪 DB 列」：{@link ObjectStorage#delete}
     * 對已經不存在的物件是冪等的（GCS 的 delete 找不到物件時回 false 而非拋例外，
     * {@link com.onlinesanta.storage.LocalObjectStorage} 用 {@code deleteIfExists}），
     * 但儲存端若真的出錯（网路、權限）會拋例外，讓交易回滾、DB 列保留——不能讓
     * 資料庫說「已刪除」而實際檔案還留在儲存端。
     */
    @Transactional
    public AttachmentDeletionResult delete(UUID attachmentId) {
        AppPrincipal principal = currentUser.require();
        Attachment attachment = attachments.findById(attachmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("附件", attachmentId));

        requireDeletablePurpose(attachment);

        boolean deletedByAdmin = principal.isAdmin();
        if (!deletedByAdmin) {
            authorizeDelete(attachment, principal);
        }

        storage.delete(attachment.getPurpose().bucket(), attachment.getObjectName());
        attachments.delete(attachment);

        return new AttachmentDeletionResult(
                attachment.getId(), attachment.getPurpose(), attachment.getOwnerId(), deletedByAdmin);
    }

    /**
     * 內部用：清理排程刪除一筆放棄的 PENDING 附件，不做任何使用者授權檢查。
     *
     * <p>清理排程沒有「使用者」這個概念——呼叫端的身分驗證（Cloud Scheduler 的
     * OIDC token，或管理端手動觸發要求的 ADMIN 角色）已經在 controller 層把關過，
     * 這裡只管刪除本身。刻意獨立於 {@link #delete}（一般使用者走的授權路徑）之外，
     * 也是為了讓 {@code PendingAttachmentCleanupService} 對每一筆呼叫都各自成為一個
     * 獨立的交易——它是不同的 Spring bean，透過代理呼叫，不會落入同一個類別內
     * 自我呼叫導致 {@code @Transactional} 失效的陷阱。
     */
    @Transactional
    public void deletePendingAttachment(UUID attachmentId) {
        Attachment attachment = attachments.findById(attachmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("附件", attachmentId));
        if (attachment.getUploadStatus() != UploadStatus.PENDING) {
            throw new IllegalStateException(
                    "清理排程只處理 PENDING 附件，這筆實際是 " + attachment.getUploadStatus());
        }

        storage.delete(attachment.getPurpose().bucket(), attachment.getObjectName());
        attachments.delete(attachment);
    }

    private void requireDeletablePurpose(Attachment attachment) {
        if (attachment.getPurpose() == AttachmentPurpose.WISH_IMAGE) {
            throw new BusinessRuleException("ATTACHMENT_NOT_DELETABLE",
                    "願望示意圖請改用「更換示意圖」上傳新圖取代，不支援直接刪除");
        }
    }

    private void authorizeDelete(Attachment attachment, AppPrincipal principal) {
        switch (attachment.getPurpose()) {
            case SHIPPING_PROOF -> {
                Claim claim = findClaim(attachment.getOwnerId());
                if (!claim.isOwnedBy(principal.userId())) {
                    throw ResourceNotFoundException.of("附件", attachment.getId());
                }
            }
            case ORG_FEEDBACK -> {
                UUID organizationId = currentUser.requireOrganizationId();
                Claim claim = findClaim(attachment.getOwnerId());
                if (!claim.getWish().getOrganization().getId().equals(organizationId)) {
                    throw ResourceNotFoundException.of("附件", attachment.getId());
                }
            }
            case WISH_IMAGE -> throw new IllegalStateException(
                    "requireDeletablePurpose 應該已經擋掉 WISH_IMAGE");
        }
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
