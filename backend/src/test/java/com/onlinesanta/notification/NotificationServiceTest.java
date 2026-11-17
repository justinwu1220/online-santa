package com.onlinesanta.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * {@link NotificationService} 的降級與失敗分級。
 *
 * <p>用單元測試而非整合測試：不需要 Spring 容器就能證明這些性質，比架一整個測試上下文
 * 簡單。這裡直接 new，{@code @Async} 因此不經代理、同步執行——這正是這個測試要的，
 * 不必等待或輪詢。
 *
 * <p>失敗分級的斷言對著<strong>實際記出來的 log</strong> 做，而不是對著計數器的 getter：
 * log 才是這個功能真正的產出（Cloud Logging 的告警掛在上面），對計數器斷言會變成
 * 「測試通過但告警其實沒記出來」。斷言只比對 {@link NotificationService#ALERT_MARKER}
 * 這個穩定的 token 與數字，不比對整句文案，所以改寫訊息不會弄壞測試。
 */
class NotificationServiceTest {

    private static final NotificationProperties UNCONFIGURED =
            new NotificationProperties(null, 587, null, null, "from@example.org", "http://localhost:5173");

    private static final NotificationProperties CONFIGURED =
            new NotificationProperties("smtp.example.org", 587, "user", "secret",
                    "from@example.org", "https://example.org");

    /**
     * 刻意是多行的——SMTP 的錯誤回應常常如此，而 {@code rootCauseMessage} 的職責之一
     * 就是把它壓成一行好 grep。
     */
    private static final String SMTP_RESPONSE = """
            452 4.3.1 Daily quota exceeded
              try again later""";

    private static final String SMTP_RESPONSE_ON_ONE_LINE =
            "452 4.3.1 Daily quota exceeded try again later";

    /** 永遠回傳 {@code null}，模擬 {@code MailConfig} 在 MAIL_HOST 未設定時的降級行為。 */
    private static final ObjectProvider<JavaMailSender> ABSENT = providerOf(null);

    private ch.qos.logback.classic.Logger serviceLogger;
    private ListAppender<ILoggingEvent> appender;
    private boolean originalAdditive;

    @BeforeEach
    void captureLogs() {
        serviceLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(NotificationService.class);
        appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
        // 這些測試會刻意製造寄信失敗，而那些都是 ERROR 加 stack trace。關掉向上傳遞，
        // CI 的輸出就不會出現一堆看起來像壞掉、其實是預期中的錯誤。
        originalAdditive = serviceLogger.isAdditive();
        serviceLogger.setAdditive(false);
    }

    @AfterEach
    void releaseLogs() {
        serviceLogger.setAdditive(originalAdditive);
        serviceLogger.detachAppender(appender);
        appender.stop();
    }

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

    @Test
    @DisplayName("寄信失敗時，伺服器的回應原文會被記下來，而且壓成一行")
    void failureLogsTheServerResponseOnOneLine() {
        NotificationService service = serviceWith(alwaysFailing());

        service.send("someone@example.com", "測試主旨", "測試內文");

        assertThat(loggedMessages())
                .anyMatch(message -> message.contains(SMTP_RESPONSE_ON_ONE_LINE));
        assertThat(loggedMessages())
                .noneMatch(message -> message.lines().count() > 1);
    }

    @Test
    @DisplayName("連續失敗還沒到門檻時，不記下告警標記")
    void doesNotAlertBelowTheThreshold() {
        NotificationService service = serviceWith(alwaysFailing());

        for (int i = 0; i < NotificationService.FAILURE_ALERT_THRESHOLD - 1; i++) {
            service.send("someone@example.com", "測試主旨", "測試內文");
        }

        assertThat(loggedMessages())
                .noneMatch(message -> message.contains(NotificationService.ALERT_MARKER));
    }

    @Test
    @DisplayName("連續失敗達到門檻時，記下帶告警標記與連續次數的 log")
    void alertsOnceTheThresholdIsReached() {
        NotificationService service = serviceWith(alwaysFailing());

        for (int i = 0; i < NotificationService.FAILURE_ALERT_THRESHOLD; i++) {
            service.send("someone@example.com", "測試主旨", "測試內文");
        }

        assertThat(loggedMessages()).anyMatch(message ->
                message.contains(NotificationService.ALERT_MARKER)
                        && message.contains("連續 " + NotificationService.FAILURE_ALERT_THRESHOLD + " 封"));
    }

    @Test
    @DisplayName("中間成功一次就把連續失敗計數歸零，所以累計四次失敗也不會告警")
    void successResetsTheConsecutiveFailureCount() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailException failure = quotaExhausted();
        // 失敗、失敗、成功、失敗、失敗：累計四次失敗，但連續最多只有兩次。
        // 計數沒有歸零的話，第四次失敗就會跨過門檻並記下告警標記。
        doThrow(failure).doThrow(failure).doNothing().doThrow(failure).doThrow(failure)
                .when(sender).send(any(SimpleMailMessage.class));
        NotificationService service = new NotificationService(providerOf(sender), CONFIGURED);

        for (int i = 0; i < 5; i++) {
            service.send("someone@example.com", "測試主旨", "測試內文");
        }

        assertThat(loggedMessages())
                .noneMatch(message -> message.contains(NotificationService.ALERT_MARKER));
    }

    @Test
    @DisplayName("恢復正常時會記一句，並說明失敗的那些通知已經永久遺失")
    void logsRecoveryAndSaysTheLostMailWillNotBeResent() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailException failure = quotaExhausted();
        doThrow(failure).doThrow(failure).doThrow(failure).doNothing()
                .when(sender).send(any(SimpleMailMessage.class));
        NotificationService service = new NotificationService(providerOf(sender), CONFIGURED);

        for (int i = 0; i < 4; i++) {
            service.send("someone@example.com", "測試主旨", "測試內文");
        }

        assertThat(loggedMessages()).anyMatch(message ->
                message.contains("已恢復")
                        && message.contains(String.valueOf(NotificationService.FAILURE_ALERT_THRESHOLD))
                        && message.contains("永久遺失"));
    }

    // ------------------------------------------------------------ 每日上限與主旨前綴

    @Test
    @DisplayName("設了主旨前綴，每封信的主旨前面都會加上")
    void subjectPrefixIsPrepended() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationService service = new NotificationService(providerOf(sender), withLimit(0, "[測試站]"));

        service.send("someone@example.com", "信箱驗證碼 123456", "內文");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo("[測試站] 信箱驗證碼 123456");
    }

    @Test
    @DisplayName("沒設前綴就維持原本的主旨")
    void subjectIsUntouchedWithoutAPrefix() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationService service = new NotificationService(providerOf(sender), CONFIGURED);

        service.send("someone@example.com", "原本的主旨", "內文");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo("原本的主旨");
    }

    @Test
    @DisplayName("沒設每日上限（0）：想寄多少就寄多少")
    void zeroMeansNoDailyLimit() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationService service = new NotificationService(providerOf(sender), withLimit(0, ""));

        for (int i = 0; i < 50; i++) {
            service.send("someone@example.com", "主旨", "內文");
        }

        verify(sender, times(50)).send(any(SimpleMailMessage.class));
        assertThat(loggedMessages()).noneMatch(message -> message.contains(NotificationService.LIMIT_REACHED_MARKER));
    }

    @Test
    @DisplayName("超過每日上限的信不再寄，並記下一次帶告警標記的 ERROR")
    void mailBeyondTheDailyLimitIsNotSent() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationService service = new NotificationService(providerOf(sender), withLimit(3, ""));

        for (int i = 0; i < 8; i++) {
            service.send("someone@example.com", "主旨", "內文");
        }

        verify(sender, times(3)).send(any(SimpleMailMessage.class));
        List<ILoggingEvent> reached = appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains(NotificationService.LIMIT_REACHED_MARKER))
                .toList();
        // 第一次被擋下是 ERROR（告警掛這條）；之後沒到每 10 封一次，不洗版
        assertThat(reached).hasSize(1);
        assertThat(reached.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(reached.get(0).getFormattedMessage()).contains("3");
    }

    @Test
    @DisplayName("一直被擋下時，每 10 封才再記一次 WARN，不洗版")
    void skippedMailIsLoggedSparingly() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationService service = new NotificationService(providerOf(sender), withLimit(1, ""));

        for (int i = 0; i < 1 + 25; i++) {
            service.send("someone@example.com", "主旨", "內文");
        }

        // 1 封寄出；擋下 25 封 → 第 1 封記 ERROR，第 10、20 封各記一次 WARN
        long logged = appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains(NotificationService.LIMIT_REACHED_MARKER))
                .count();
        assertThat(logged).isEqualTo(3);
    }

    @Test
    @DisplayName("用掉上限的 80% 時記一次提醒，還沒停寄")
    void warnsOnceWhenNearingTheLimit() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationService service = new NotificationService(providerOf(sender), withLimit(10, ""));

        for (int i = 0; i < 9; i++) {
            service.send("someone@example.com", "主旨", "內文");
        }

        verify(sender, times(9)).send(any(SimpleMailMessage.class));
        assertThat(appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains(NotificationService.LIMIT_NEARING_MARKER))
                .count()).isEqualTo(1);
        assertThat(loggedMessages()).noneMatch(message -> message.contains(NotificationService.LIMIT_REACHED_MARKER));
    }

    @Test
    @DisplayName("隔天（UTC）重新計算，額度恢復")
    void theLimitResetsOnTheNextUtcDay() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-11-17T23:59:00Z"));
        NotificationService service = new NotificationService(providerOf(sender), withLimit(2, ""), clock);

        service.send("someone@example.com", "主旨", "內文");
        service.send("someone@example.com", "主旨", "內文");
        service.send("someone@example.com", "被擋下", "內文");
        verify(sender, times(2)).send(any(SimpleMailMessage.class));

        clock.advanceSeconds(120); // 跨過 UTC 午夜
        service.send("someone@example.com", "新的一天", "內文");
        service.send("someone@example.com", "新的一天", "內文");
        service.send("someone@example.com", "又被擋下", "內文");

        verify(sender, times(4)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("寄失敗的信也佔名額：一直失敗一直重試正是最需要擋下的情況")
    void failedMailStillCountsTowardsTheLimit() {
        JavaMailSender sender = alwaysFailing();
        NotificationService service = new NotificationService(providerOf(sender), withLimit(3, ""));

        for (int i = 0; i < 6; i++) {
            service.send("someone@example.com", "主旨", "內文");
        }

        verify(sender, times(3)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("沒設定寄信服務（no-op）不會佔名額")
    void noOpModeDoesNotUseTheQuota() {
        NotificationService service = new NotificationService(ABSENT, withLimit(1, ""));

        for (int i = 0; i < 5; i++) {
            service.send("someone@example.com", "主旨", "內文");
        }

        assertThat(loggedMessages()).noneMatch(message -> message.contains(NotificationService.LIMIT_REACHED_MARKER));
    }

    private static NotificationProperties withLimit(int dailyLimit, String subjectPrefix) {
        return new NotificationProperties("smtp.example.org", 587, "user", "secret",
                "from@example.org", "https://example.org", dailyLimit, subjectPrefix);
    }

    /** 可以手動往後撥的時鐘（UTC）。 */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private NotificationService serviceWith(JavaMailSender sender) {
        return new NotificationService(providerOf(sender), CONFIGURED);
    }

    private static JavaMailSender alwaysFailing() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(quotaExhausted()).when(sender).send(any(SimpleMailMessage.class));
        return sender;
    }

    /** 包成 {@link MailSendException} 的底層原因，形狀與真實的 SMTP 失敗一致。 */
    private static MailException quotaExhausted() {
        return new MailSendException("寄送失敗", new RuntimeException(SMTP_RESPONSE));
    }

    private List<String> loggedMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static ObjectProvider<JavaMailSender> providerOf(JavaMailSender sender) {
        return new ObjectProvider<>() {
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
                return sender;
            }

            @Override
            public JavaMailSender getIfUnique() {
                return sender;
            }
        };
    }
}
