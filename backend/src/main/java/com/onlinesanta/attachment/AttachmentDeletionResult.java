package com.onlinesanta.attachment;

import java.util.UUID;

/**
 * {@link AttachmentService#delete} 的結果，供呼叫端（{@code AttachmentController}）
 * 判斷是否要寫入管理員稽核紀錄、寫哪一種。
 *
 * @param ownerId 附件擁有者的 id，依用途而定：SHIPPING_PROOF／ORG_FEEDBACK 是認領，
 *                WISH_LETTER 是願望，ORG_DOCUMENT 是機構
 */
public record AttachmentDeletionResult(
        UUID attachmentId, AttachmentPurpose purpose, UUID ownerId, boolean deletedByAdmin) {
}
