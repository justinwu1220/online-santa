package com.onlinesanta.admin.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.message.Message;

/**
 * 管理員檢視認領對話。
 *
 * <p>比照 {@link com.onlinesanta.message.dto.MessageView} 不帶發言者身分的原則，只標示
 * 角色（捐贈者／機構），不附姓名或 email——姓名與 email 已經在認領詳情本身看得到，
 * 這裡沒有必要重複曝露。
 *
 * <p>這是認領詳情的一部分：開啟詳情時已經寫入 {@code VIEW_CLAIM_DETAIL} 稽核，這裡
 * 不另外記一筆。
 */
public record AdminMessageView(Long id, SenderRole senderRole, String body, Instant sentAt) {

    public enum SenderRole { DONOR, ORGANIZATION }

    public static AdminMessageView from(Message message, UUID donorUserId) {
        return new AdminMessageView(
                message.getId(),
                message.isSentBy(donorUserId) ? SenderRole.DONOR : SenderRole.ORGANIZATION,
                message.getBody(),
                message.getCreatedAt());
    }
}
