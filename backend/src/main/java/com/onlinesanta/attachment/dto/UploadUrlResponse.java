package com.onlinesanta.attachment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 前端直傳所需的資訊。
 *
 * @param attachmentId 上傳完成後要拿它呼叫 confirm 端點
 * @param uploadUrl    直接 PUT 的目標，不經過本服務
 * @param contentType  PUT 時必須帶上，與簽章綁定
 */
public record UploadUrlResponse(
        UUID attachmentId,
        String uploadUrl,
        String contentType,
        Instant expiresAt) {
}
