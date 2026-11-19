package com.onlinesanta.notification;

/**
 * 信件附件（目前只有結案文件：紅聯收據 PDF、機構確認表 PDF）。
 *
 * @param fileName    收件人看到的檔名；允許中文，寄信時以 UTF-8 編碼
 * @param contentType 例如 application/pdf
 */
public record MailAttachment(String fileName, String contentType, byte[] content) {
}
