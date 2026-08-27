package com.onlinesanta.message.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.message.Message;

/**
 * 一則對話訊息。
 *
 * <p>不回傳寄件者的 id 或 email：對話雙方的身分由 {@code fromMe} 表達就夠了，
 * 沒有必要把對方的識別資訊送給前端。
 *
 * @param fromMe 是否為自己送出的訊息，供前端決定氣泡靠左或靠右
 */
public record MessageView(
        Long id,
        String body,
        boolean fromMe,
        boolean read,
        Instant sentAt) {

    public static MessageView from(Message message, UUID viewerId) {
        return new MessageView(
                message.getId(),
                message.getBody(),
                message.isSentBy(viewerId),
                message.getReadAt() != null,
                message.getCreatedAt());
    }
}
