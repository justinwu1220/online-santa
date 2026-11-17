package com.onlinesanta.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * {@link MailConfig} 的「全有或全無」。
 *
 * <p>和 {@link NotificationServiceTest} 一樣直接 new，不架 Spring 容器——這裡要證明的
 * 全部都在那個 {@code @Bean} method 裡。
 *
 * <p>這組測試真正要守的是一個在測試裡抓不到、只會在真實收件匣裡現形的錯誤：五個
 * {@code MAIL_*} 值裡只補了 {@code MAIL_HOST}。那會讓寄信層啟用，但每封信都從
 * {@code application.yml} 的預設寄件人寄出、內文連結指向 localhost。
 */
class MailConfigTest {

    private final MailConfig config = new MailConfig();

    private static NotificationProperties props(
            String host, String username, String password, String from, String publicUrl) {
        return new NotificationProperties(host, 587, username, password, from, publicUrl);
    }

    private static NotificationProperties complete() {
        return props("email-smtp.ap-northeast-1.amazonaws.com",
                "AKIAEXAMPLE", "smtp-password", "no-reply@onlinesanta.example", "https://onlinesanta.example");
    }

    @Test
    @DisplayName("MAIL_HOST 未設定時回傳 null，代表 bean 不存在，通知降級為 no-op")
    void returnsNullWhenUnconfigured() {
        assertThat(config.javaMailSender(props(null, null, null, null, null))).isNull();
        assertThat(config.javaMailSender(props("   ", null, null, null, null))).isNull();
    }

    @Test
    @DisplayName("MAIL_HOST 未設定時不檢查其他值——本機開發不該因為沒填寄件人就起不來")
    void skipsVerificationWhenUnconfigured() {
        // from 是 application.yml 的預設值、publicUrl 指向本機，正是本機開發的樣子
        assertThat(config.javaMailSender(props(null, null, null, "no-reply@example.org", "http://localhost:5173")))
                .isNull();
    }

    @Test
    @DisplayName("設了 MAIL_HOST 卻沒改 MAIL_FROM 的預設值時，啟動失敗")
    void rejectsPlaceholderFromAddress() {
        NotificationProperties onlyHost = props("email-smtp.ap-northeast-1.amazonaws.com",
                "AKIAEXAMPLE", "smtp-password", "no-reply@example.org", "https://onlinesanta.example");

        assertThatThrownBy(() -> config.javaMailSender(onlyHost))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
    }

    @Test
    @DisplayName("設了 MAIL_HOST 卻沒有 MAIL_FROM 時，啟動失敗")
    void rejectsBlankFromAddress() {
        assertThatThrownBy(() -> config.javaMailSender(props(
                "email-smtp.ap-northeast-1.amazonaws.com", "AKIAEXAMPLE", "smtp-password", "  ",
                "https://onlinesanta.example")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
    }

    @Test
    @DisplayName("設了 MAIL_HOST 卻沒有 SMTP 帳密時，啟動失敗——否則整季的通知信都會安靜地失敗")
    void rejectsMissingCredentials() {
        assertThatThrownBy(() -> config.javaMailSender(props(
                "email-smtp.ap-northeast-1.amazonaws.com", null, "smtp-password",
                "no-reply@onlinesanta.example", "https://onlinesanta.example")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_USERNAME");

        assertThatThrownBy(() -> config.javaMailSender(props(
                "email-smtp.ap-northeast-1.amazonaws.com", "AKIAEXAMPLE", "   ",
                "no-reply@onlinesanta.example", "https://onlinesanta.example")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_PASSWORD");
    }

    @Test
    @DisplayName("設了 MAIL_HOST 卻沒有 APP_PUBLIC_URL 時，啟動失敗")
    void rejectsBlankPublicUrl() {
        assertThatThrownBy(() -> config.javaMailSender(props(
                "email-smtp.ap-northeast-1.amazonaws.com", "AKIAEXAMPLE", "smtp-password",
                "no-reply@onlinesanta.example", "  ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_PUBLIC_URL");
    }

    @Test
    @DisplayName("APP_PUBLIC_URL 指向本機只警告不擋——本機拿假 SMTP 試版面是合理的")
    void allowsLocalhostPublicUrl() {
        assertThat(config.javaMailSender(props(
                "localhost", "user", "pass", "no-reply@onlinesanta.example", "http://localhost:5173")))
                .isNotNull();
    }

    @Test
    @DisplayName("五個值齊全時建出 sender，並且要求 STARTTLS 而非只是提供")
    void buildsSenderRequiringStartTls() {
        JavaMailSender sender = config.javaMailSender(complete());

        assertThat(sender).isInstanceOf(JavaMailSenderImpl.class);
        JavaMailSenderImpl impl = (JavaMailSenderImpl) sender;
        assertThat(impl.getHost()).isEqualTo("email-smtp.ap-northeast-1.amazonaws.com");
        assertThat(impl.getPort()).isEqualTo(587);
        assertThat(impl.getJavaMailProperties())
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.starttls.enable", "true")
                // enable 只代表「對方支援就升級」，不支援時會靜靜地以明文送出帳密
                .containsEntry("mail.smtp.starttls.required", "true");
    }
}
