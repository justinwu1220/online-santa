package com.onlinesanta.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email 通知的設定（{@code app.notification.*}）。
 *
 * @param host        SMTP 主機。留空代表尚未設定，{@link MailConfig} 不會建立
 *                    {@code JavaMailSender}，通知一律降級為 no-op（只記 log）
 * @param port        SMTP 連接埠
 * @param username    SMTP 帳號
 * @param password    SMTP 密碼
 * @param fromAddress 信件的寄件人地址
 * @param publicUrl   前端網站的網址，信件內文的連結靠它組出來——這支 API 自己的網址
 *                    使用者到不了
 */
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        String host,
        int port,
        String username,
        String password,
        String fromAddress,
        String publicUrl) {

    public boolean isConfigured() {
        return host != null && !host.isBlank();
    }
}
