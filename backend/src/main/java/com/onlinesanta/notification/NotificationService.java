package com.onlinesanta.notification;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
 *
 * <p><strong>每日上限</strong>（{@code app.notification.daily-limit}）是反方向的保護：上面講的是
 * 「額度用完了，要有人知道」，這裡是「別讓額度被燒光」。註冊與忘記密碼都會寄信，被機器人批次註冊、
 * 或某個環境出錯陷入迴圈，都能一天內用掉整個月的額度；多個環境共用同一個寄信帳號時，一個環境出事
 * 就會讓所有環境的驗證碼寄不出去。超過上限的信<strong>直接不寄</strong>（同樣不會重寄），
 * 並記下 {@link #LIMIT_REACHED_MARKER} 讓監控告警。
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
     * 今天已寄到每日上限，之後的信不再寄。<strong>同樣是 Cloud Logging 告警掛的 token，不可隨文案改。</strong>
     */
    static final String LIMIT_REACHED_MARKER = "MAIL_DAILY_LIMIT_REACHED";

    /** 用掉上限的 80%——還沒停寄，但該去看看是不是有東西在狂寄信。 */
    static final String LIMIT_NEARING_MARKER = "MAIL_DAILY_LIMIT_NEARING";

    /** 超過上限之後，每被擋下這麼多封才再記一次 WARN，避免迴圈出錯時 log 被洗版。 */
    private static final int SKIPPED_LOG_INTERVAL = 10;

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
    private final Clock clock;

    /**
     * 每日額度的計數。用 UTC 日期歸零，對齊寄信供應商以 UTC 計算的每日額度。
     *
     * <p>計數只針對「這個行程」：Cloud Run 多個實例各算各的，總量可能略超過上限。
     * 這是軟性的保護，要的是擋住失控的迴圈，不是精確計量。上面「寄信不加鎖」的理由在這裡不適用——
     * 這把鎖只包住幾個整數的比較與遞增，不包住真正的寄送。
     */
    private final Object dailyLock = new Object();
    private LocalDate dailyDay;
    private int sentToday;
    private int skippedToday;

    @Autowired
    public NotificationService(ObjectProvider<JavaMailSender> mailSender,
                               NotificationProperties properties) {
        this(mailSender, properties, Clock.systemUTC());
    }

    /** 測試用：可以指定時鐘，模擬跨日。 */
    NotificationService(ObjectProvider<JavaMailSender> mailSender,
                        NotificationProperties properties, Clock clock) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.clock = clock;
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

        // 主旨前綴（例如測試站的「[測試站]」）：收件人一眼就能分辨這封信不是正式站寄的
        String fullSubject = properties.subjectPrefix().isEmpty()
                ? subject : properties.subjectPrefix() + " " + subject;

        if (!reserveDailySlot()) {
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.fromAddress());
            message.setTo(to);
            message.setSubject(fullSubject);
            message.setText(body);
            sender.send(message);
            log.info("通知信已寄出。收件人＝{}，主旨＝{}", to, fullSubject);
            recordSuccess();
        } catch (MailException e) {
            recordFailure(to, fullSubject, e);
        }
    }

    /**
     * 搶一個今天的寄信名額。沒設上限（0）一律放行。
     *
     * <p>先算名額再寄：寄失敗的信也會佔掉名額。偏保守是刻意的——失敗的那一封也許已經被供應商
     * 計入額度，而且「一直失敗一直重試」正是最需要被擋下來的情況。
     *
     * @return 這封信是否可以寄
     */
    private boolean reserveDailySlot() {
        int limit = properties.dailyLimit();
        if (limit <= 0) {
            return true;
        }
        synchronized (dailyLock) {
            LocalDate today = LocalDate.now(clock);
            if (!today.equals(dailyDay)) {
                dailyDay = today;
                sentToday = 0;
                skippedToday = 0;
            }

            if (sentToday >= limit) {
                skippedToday++;
                // 第一次擋下用 ERROR（告警掛這條），之後降成每隔幾封一次的 WARN
                if (skippedToday == 1) {
                    log.error("{}：今天（UTC {}）已寄出 {} 封，達到每日上限 {}。之後的通知信與驗證碼不再寄出，"
                                    + "而且不會重寄。請檢查是不是有東西在大量寄信（機器人批次註冊、迴圈）。",
                            LIMIT_REACHED_MARKER, today, sentToday, limit);
                } else if (skippedToday % SKIPPED_LOG_INTERVAL == 0) {
                    log.warn("{}：今天已經擋下 {} 封超過上限的信。", LIMIT_REACHED_MARKER, skippedToday);
                }
                return false;
            }

            sentToday++;
            // 剛好跨過 80% 的那一封才記，只會出現一次
            if (limit >= 5 && sentToday == (int) Math.ceil(limit * 0.8)) {
                log.warn("{}：今天（UTC {}）已寄出 {} 封，用掉每日上限 {} 的 80%。",
                        LIMIT_NEARING_MARKER, today, sentToday, limit);
            }
            return true;
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
