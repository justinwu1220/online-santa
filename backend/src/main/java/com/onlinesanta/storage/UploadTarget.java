package com.onlinesanta.storage;

import java.time.Instant;

/**
 * 前端上傳所需的全部資訊。
 *
 * @param url         PUT 的目標網址
 * @param contentType 上傳時必須帶上的 Content-Type，與簽章綁定
 * @param expiresAt   逾時後網址失效
 */
public record UploadTarget(String url, String contentType, Instant expiresAt) {
}
