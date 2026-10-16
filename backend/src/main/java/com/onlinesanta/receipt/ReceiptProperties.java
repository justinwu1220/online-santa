package com.onlinesanta.receipt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 捐贈收據相關設定（{@code app.receipt.*}）。
 *
 * @param encryptionKey 加密身分證字號／統一編號用的金鑰：base64 編碼的 32 bytes
 *                      （{@code openssl rand -base64 32}）。<strong>金鑰遺失就無法解密已存的證號</strong>，
 *                      要另外備份。正式環境必填，本機與測試留空會退回內建的開發用金鑰
 */
@ConfigurationProperties(prefix = "app.receipt")
public record ReceiptProperties(String encryptionKey) {
}
