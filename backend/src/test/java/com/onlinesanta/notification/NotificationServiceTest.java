package com.onlinesanta.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * {@link NotificationService} 的 no-op 降級。
 *
 * <p>用單元測試而非整合測試：不需要 Spring 容器就能證明「沒有 JavaMailSender 可用時
 * 呼叫 {@code send} 不會拋例外」，比架一整個測試上下文簡單。這裡直接 new，
 * {@code @Async} 因此不經代理、同步執行——這正是這個測試要的，不必等待或輪詢。
 */
class NotificationServiceTest {

    private static final NotificationProperties UNCONFIGURED =
            new NotificationProperties(null, 587, null, null, "from@example.org", "http://localhost:5173");

    /** 永遠回傳 {@code null}，模擬 {@code MailConfig} 在 MAIL_HOST 未設定時的降級行為。 */
    private static final ObjectProvider<JavaMailSender> ABSENT = new ObjectProvider<>() {
        @Override
        public JavaMailSender getObject() {
            throw new IllegalStateException("測試不應該呼叫到這裡");
        }

        @Override
        public JavaMailSender getObject(Object... args) {
            throw new IllegalStateException("測試不應該呼叫到這裡");
        }

        @Override
        public JavaMailSender getIfAvailable() {
            return null;
        }

        @Override
        public JavaMailSender getIfUnique() {
            return null;
        }
    };

    @Test
    @DisplayName("MAIL_HOST 未設定時，send 不會拋例外，只是安靜地不寄信")
    void sendIsNoOpWhenMailSenderIsUnavailable() {
        NotificationService service = new NotificationService(ABSENT, UNCONFIGURED);

        service.send("someone@example.com", "測試主旨", "測試內文");
        // 沒有拋例外就是通過——no-op 模式下這是唯一該發生的事
    }

    @Test
    @DisplayName("收件人是空字串或 null 時，send 一樣不會拋例外")
    void sendIsNoOpWhenRecipientIsBlank() {
        NotificationService service = new NotificationService(ABSENT, UNCONFIGURED);

        service.send(null, "測試主旨", "測試內文");
        service.send("  ", "測試主旨", "測試內文");
    }
}
