package com.onlinesanta.message.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.message.Message;
import com.onlinesanta.message.MessageKind;

/**
 * 一則對話訊息。
 *
 * <p>不回傳寄件者的 id 或 email：對話雙方的身分由 {@code fromMe} 表達就夠了，
 * 沒有必要把對方的識別資訊送給前端。
 *
 * @param fromMe 是否為「我這一方」送出的訊息，供前端決定氣泡靠左或靠右。認領當下的招呼
 *               （WELCOME）算機構這一方送的；系統通知（SYSTEM）不屬於任何一方
 * @param kind   一般訊息（USER）、系統通知（SYSTEM，虛線框置中）或認領招呼（WELCOME）
 */
public record MessageView(
        Long id,
        String body,
        boolean fromMe,
        boolean read,
        Instant sentAt,
        MessageKind kind) {

    public static MessageView from(Message message, UUID viewerId, boolean viewerIsOrganization) {
        boolean fromMe = switch (message.getKind()) {
            case USER -> message.isSentBy(viewerId);
            case WELCOME -> viewerIsOrganization;
            case SYSTEM -> false;
        };
        return new MessageView(
                message.getId(),
                message.getBody(),
                fromMe,
                message.getReadAt() != null,
                message.getCreatedAt(),
                message.getKind());
    }
}
