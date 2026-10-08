package com.onlinesanta.attachment.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.attachment.AttachmentPurpose;

/**
 * 一個已確認的附件。
 *
 * @param url      公開附件為固定網址；私密附件為限時簽章網址，過期後失效
 * @param fileName 使用者端的原始檔名，可能是 null——這支功能上線前的舊附件
 *                 沒有這項資訊，前端應該準備好退回通用文字
 */
public record AttachmentView(
        UUID id,
        AttachmentPurpose purpose,
        String url,
        String contentType,
        Long sizeBytes,
        Instant uploadedAt,
        String fileName) {
}
