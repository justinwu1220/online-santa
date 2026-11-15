package com.onlinesanta.notification;

import java.util.concurrent.atomic.AtomicInteger;

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
 *
 * <p><strong>為什麼要分「單次失敗」與「整體失效」</strong>：吞掉例外讓業務不受影響，
 * 代價是寄信功能可以整體壞掉而沒有任何人發現——每封信各自記一行 ERROR，看起來跟
 * 偶發的暫時性失敗一模一樣。三種會讓「之後每一封都寄不出去」的狀況尤其危險：
 * 寄信額度耗盡、SMTP 帳密失效、供應商異常。而且**沒有任何一層會重寄**：
 * 這裡不重試，Resend 的額度拒絕也不排隊不重試，所以那些通知是永久遺失的。
 * 因此連續失敗達到 {@link #FAILURE_ALERT_THRESHOLD} 次就額外記一行帶
 * {@link #ALERT_MARKER} 的 log，讓監控有一個穩定的字串可以掛告警。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * 連續失敗達到這個次數，就從「單封寄送失敗」升級成「寄信功能可能整體失效」。
     *
     * <p>取 3：單一封失敗在正常運作下也會偶爾發生（對方信箱暫時滿了、網路抖動），
     * 不值得告警；連續三封都失敗則幾乎不可能是巧合。
     */
    static final int FAILURE_ALERT_THRESHOLD = 3;

    /**
     * 升級後的 log 固定帶這個字串。
     *
     * <p><strong>Cloud Logging 的告警條件掛在這個 token 上，所以它不可以隨訊息文案
     * 一起改。</strong>要改的話記得同步改掉監控設定，否則告警會靜默失效——而「設定看
     * 起來還在、實際不會觸發」正是這類東西最難發現的壞法。
     */
    static final String ALERT_MARKER = "MAIL_FAILING_REPEATEDLY";

    /**
     * 連續寄信失敗的次數；成功一次就歸零。
     *
     * <p>執行緒池上限 5 條，所以必須是 atomic。但這只是**告警用的啟發式信號，不是
     * 精確計數**：併發下「某條執行緒成功並歸零」與「另一條失敗並遞增」可以交錯，
     * 計數因此會有少量誤差。這個誤差不影響它要偵測的東西（整體失效時所有執行緒都在
     * 失敗，計數只會往上衝），所以**不要為了精確而加鎖**——鎖會讓寄信互相等待，
     * 換來的精確度沒有任何用途。
     */
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

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
            recordSuccess();
        } catch (MailException e) {
            recordFailure(to, subject, e);
        }
    }

    private void recordSuccess() {
        int previousFailures = consecutiveFailures.getAndSet(0);
        if (previousFailures >= FAILURE_ALERT_THRESHOLD) {
            log.warn("寄信已恢復正常。先前連續失敗 {} 封，那些通知不會自動重寄，已經永久遺失。",
                    previousFailures);
        }
    }

    private void recordFailure(String to, String subject, MailException e) {
        int failures = consecutiveFailures.incrementAndGet();

        // 伺服器回應原文單獨拉出來，不只埋在 stack trace 裡：供應商用什麼字串表示
        // 「額度耗盡」各家不同（Resend 的文件只寫了 API 回 429，沒寫 SMTP 回什麼），
        // 所以先把原文記下來，第一次真的發生時就知道該對什麼做判斷。
        log.error("通知信寄送失敗，不影響已完成的業務操作。收件人＝{}，主旨＝{}，伺服器回應＝{}",
                to, subject, rootCauseMessage(e), e);

        if (failures >= FAILURE_ALERT_THRESHOLD) {
            // 刻意不帶收件人：這一行講的是系統狀態，不是某一封信。
            log.error("{}：連續 {} 封通知信寄不出去，寄信功能可能整體失效"
                            + "（寄信額度耗盡、SMTP 帳密失效或供應商異常）。最近一次的伺服器回應＝{}",
                    ALERT_MARKER, failures, rootCauseMessage(e));
        }
    }

    /**
     * 取最底層的原因訊息，並把換行壓成空白。
     *
     * <p>SMTP 的錯誤回應常常是多行的，壓成一行才 grep 得動。
     */
    private static String rootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return String.join(" ", message.strip().lines().map(String::strip).toList());
    }
}
