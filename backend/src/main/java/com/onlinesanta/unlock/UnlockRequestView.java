package com.onlinesanta.unlock;

import java.time.Instant;

/**
 * 解除申請的摘要，機構看自己的進度、管理員審核時都用這個。
 *
 * @param revision 機構修改後的內容；沒有修改為 null
 */
public record UnlockRequestView(
        Long id,
        UnlockStatus status,
        String message,
        WishRevision revision,
        String decisionNote,
        Instant createdAt,
        Instant decidedAt) {
}
