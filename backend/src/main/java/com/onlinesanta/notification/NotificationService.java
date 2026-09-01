package com.onlinesanta.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 寄送通知信。
 *
 * <p><strong>寄信是 best-effort，絕不能讓業務交易失敗或回滾</strong>——認領成立了、
 * 審核過了，這些事已經發生，通知信寄不出去不該撤銷它們。{@link #send} 因此把
 * {@link MailException} 整個吃下來，只記錯誤 log。
 *
 * <p><strong>非同步</strong>：{@code @Async} 讓寄信不擋住呼叫端的請求執行緒。呼叫端
 * 一律是 {@code notification} 套件裡的 {@code @TransactionalEventListener}，而不是
 * 業務服務直接呼叫這個類別自己的其他方法——如果在同一個類別內自我呼叫
 * {@code @Async} 方法，Spring AOP 的代理不會被觸發，註解形同虛設，呼叫會同步執行。
 * 這裡透過注入呼叫（不同的 Spring bean），代理正常生效。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final NotificationProperties properties;

    public NotificationService(ObjectProvider<JavaMailSender> mailSender,
                               NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async("notificationExecutor")
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("略過寄信：收件人信箱是空的。主旨＝{}", subject);
            return;
        }

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("[no-op] 通知信未真的寄出（MAIL_HOST 未設定）。收件人＝{}，主旨＝{}", to, subject);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.fromAddress());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("通知信已寄出。收件人＝{}，主旨＝{}", to, subject);
        } catch (MailException e) {
            log.error("通知信寄送失敗，不影響已完成的業務操作。收件人＝{}，主旨＝{}", to, subject, e);
        }
    }
}
