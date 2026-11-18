package com.onlinesanta.closing.dto;

import java.time.Instant;

/** 短效下載網址：檔案內容不經過後端，由雲端儲存直接給瀏覽器。 */
public record ReceiptDownloadView(String url, Instant expiresAt) {
}
