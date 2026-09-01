package com.onlinesanta.notification;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * {@link JavaMailSender} 的組裝。
 *
 * <p>刻意不靠 Spring Boot 的 {@code spring.mail.*} 自動組態：那一套的無縫降級條件
 * 是「{@code spring.mail.host} 這個屬性完全不存在」，但這裡的 yml 一律用
 * {@code ${MAIL_HOST:}} 這種有預設值的寫法讓本機開發不必設環境變數，屬性因此
 * 永遠「存在」（值是空字串），{@code @ConditionalOnProperty} 之類的條件式會誤判
 * 成「已設定」。改成這裡自己判斷 {@link NotificationProperties#isConfigured()}，
 * 未設定時這個 {@code @Bean} method 直接回傳 {@code null}——Spring 允許這麼做，
 * 代表「這個 bean 不存在」，{@link NotificationService} 用
 * {@code ObjectProvider<JavaMailSender>} 接住就會拿到空，藉此自然降級成 no-op，
 * 不需要另外維護一份「是否啟用通知」的旗標。
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    public JavaMailSender javaMailSender(NotificationProperties properties) {
        if (!properties.isConfigured()) {
            log.info("MAIL_HOST 未設定，Email 通知將以 no-op 模式運作（只記 log，不寄信）");
            return null;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setUsername(properties.username());
        sender.setPassword(properties.password());

        Properties mailProperties = sender.getJavaMailProperties();
        mailProperties.put("mail.smtp.auth", "true");
        mailProperties.put("mail.smtp.starttls.enable", "true");
        return sender;
    }
}
