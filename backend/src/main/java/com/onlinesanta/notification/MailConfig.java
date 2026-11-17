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
 *
 * <p><strong>設定齊全與否是「全有或全無」，由下面的 {@code verify} 在啟動時強制。</strong>
 * 五個值裡只補 {@code MAIL_HOST} 比完全不設更糟：寄信層會啟用，但每封信都從
 * {@code no-reply@example.org}（{@code MAIL_FROM} 的預設值）寄出、內文連結指向
 * {@code http://localhost:5173}（{@code APP_PUBLIC_URL} 的預設值）。前者會被收信端
 * 以寄件人網域未驗證為由退回或判為垃圾信，後者則是使用者點了也到不了的連結。
 * 兩者都只會在真實收件匣裡現形，測試抓不到，所以改成讓它在部署時就起不來。
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /** {@code application.yml} 裡 {@code MAIL_FROM} 的預設值，本身不是可用的寄件人。 */
    private static final String PLACEHOLDER_FROM = "no-reply@example.org";

    @Bean
    public JavaMailSender javaMailSender(NotificationProperties properties) {
        if (!properties.isConfigured()) {
            log.info("MAIL_HOST 未設定，Email 通知將以 no-op 模式運作（只記 log，不寄信）");
            return null;
        }
        verify(properties);

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setUsername(properties.username());
        sender.setPassword(properties.password());

        Properties mailProperties = sender.getJavaMailProperties();
        mailProperties.put("mail.smtp.auth", "true");
        mailProperties.put("mail.smtp.starttls.enable", "true");
        // enable 只是「對方支援就升級成 TLS」，不支援時會靜靜地以明文送出帳密。
        // required 讓升級失敗直接變成錯誤——SMTP 憑證不該有降級成明文的路徑。
        mailProperties.put("mail.smtp.starttls.required", "true");
        return sender;
    }

    /**
     * MAIL_HOST 一旦設定，其餘必要的值就不能還留著預設值。
     *
     * <p>公開網址只警告不擋：本機拿 MailHog 之類的假 SMTP 試版面時，
     * {@code localhost} 的連結才是對的。
     */
    private void verify(NotificationProperties properties) {
        String from = properties.fromAddress();
        if (from == null || from.isBlank() || PLACEHOLDER_FROM.equals(from)) {
            throw new IllegalStateException(
                    "設定了 MAIL_HOST 卻沒有可用的 MAIL_FROM（目前是「"
                    + (from == null || from.isBlank() ? "空值" : from)
                    + "」）。寄件人必須是寄信服務上已驗證的網域，否則信會被退回或判為垃圾信。");
        }
        if (properties.username() == null || properties.username().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException(
                    "設定了 MAIL_HOST 卻沒有 MAIL_USERNAME / MAIL_PASSWORD。"
                    + "SMTP 連線會在驗證階段被拒絕，而通知信的失敗是被吞掉的（見 NotificationService），"
                    + "所以這裡要在啟動時就擋下來，而不是讓它變成一整季都沒人收到信。");
        }

        String publicUrl = properties.publicUrl();
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new IllegalStateException(
                    "設定了 MAIL_HOST 卻沒有 APP_PUBLIC_URL。信件內文的連結組不出來。");
        }
        if (publicUrl.contains("localhost") || publicUrl.contains("127.0.0.1")) {
            log.warn("APP_PUBLIC_URL 仍指向本機（{}），信件裡的連結收件人點了到不了。"
                    + "若這不是本機測試，請改成前端的正式網址", publicUrl);
        }
    }
}
