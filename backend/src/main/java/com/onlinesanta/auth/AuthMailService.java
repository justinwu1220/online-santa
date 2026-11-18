package com.onlinesanta.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.onlinesanta.notification.MailFields;
import com.onlinesanta.notification.MailTemplateId;
import com.onlinesanta.notification.NotificationProperties;
import com.onlinesanta.notification.NotificationService;

/**
 * 帳號相關的信：信箱驗證碼與重設密碼連結。
 *
 * <p>一律在交易<strong>提交之後</strong>才寄——交易 rollback 的話，寄出去的驗證碼在資料庫裡
 * 根本不存在，使用者拿著它也驗證不了。
 *
 * <p>沒設定 SMTP（{@code MAIL_HOST} 空白，通常是本機）時，{@link NotificationService} 只會記一行
 * 「未真的寄出」，驗證碼與連結就拿不到了，所以這裡額外把內容寫進 log，開發時才走得完流程。
 */
@Service
public class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);

    static final int CODE_TTL_MINUTES = 15;
    static final int RESET_TTL_MINUTES = 60;

    private final NotificationService notifications;
    private final NotificationProperties properties;

    public AuthMailService(NotificationService notifications, NotificationProperties properties) {
        this.notifications = notifications;
        this.properties = properties;
    }

    public void sendVerificationCode(String to, String code) {
        if (!properties.isConfigured()) {
            log.info("[dev-mail] 信箱驗證碼 {}（收件人＝{}）", code, to);
        }
        afterCommit(() -> notifications.sendTemplate(to, MailTemplateId.ACCOUNT_VERIFICATION_CODE,
                MailFields.of("code", code, "codeMinutes", CODE_TTL_MINUTES)));
    }

    public void sendPasswordReset(String to, String token) {
        String link = properties.publicUrl() + "/reset-password?token=" + token;
        if (!properties.isConfigured()) {
            log.info("[dev-mail] 重設密碼連結 {}（收件人＝{}）", link, to);
        }
        afterCommit(() -> notifications.sendTemplate(to, MailTemplateId.PASSWORD_RESET,
                MailFields.of("resetUrl", link, "resetMinutes", RESET_TTL_MINUTES)));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
