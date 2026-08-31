package com.onlinesanta.attachment;

import java.util.UUID;

/**
 * {@link AttachmentService#delete} 的結果，供呼叫端（{@code AttachmentController}）
 * 判斷是否要寫入管理員稽核紀錄。
 *
 * @param claimId 附件所屬的認領 id——SHIPPING_PROOF／ORG_FEEDBACK 的 ownerId 就是 claimId
 */
public record AttachmentDeletionResult(
        UUID attachmentId, AttachmentPurpose purpose, UUID claimId, boolean deletedByAdmin) {
}
