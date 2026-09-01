package com.onlinesanta.job;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.job.dto.DeadlineReminderResult;
import com.onlinesanta.notification.NotificationFormat;
import com.onlinesanta.notification.NotificationProperties;
import com.onlinesanta.notification.NotificationService;

/**
 * 寄送期限快到（未來 2 天內）的認領，提醒捐贈者盡快寄出。
 *
 * <p>直接操作 {@link ClaimRepository} 與 {@link Claim}，不透過
 * {@code ClaimService}——比照 {@link ClaimReleaseService} 的既有寫法，排程對認領的
 * 操作走 job 套件自己的路徑，不借道請求端的應用服務層。
 *
 * <p><strong>逐筆容錯，單筆失敗不擋整批</strong>，理由與寫法比照
 * {@link PendingAttachmentCleanupService}：每一筆都是獨立交易，用
 * {@code @Lazy} 注入自己（{@link #self}）呼叫 {@link #markAndNotify}——同一個類別內
 * 直接呼叫 {@code this.markAndNotify(...)} 不會經過 Spring AOP 代理，
 * {@code @Transactional} 會被靜默略過；透過注入的自我代理呼叫，才會是真正獨立的交易。
 */
@Service
public class DeadlineReminderService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderService.class);

    /** 期限前 2 天開始提醒。 */
    static final Duration REMINDER_WINDOW = Duration.ofDays(2);

    private final ClaimRepository claims;
    private final NotificationService notifications;
    private final NotificationProperties properties;
    private final DeadlineReminderService self;

    public DeadlineReminderService(ClaimRepository claims,
                                   NotificationService notifications,
                                   NotificationProperties properties,
                                   @Lazy DeadlineReminderService self) {
        this.claims = claims;
        this.notifications = notifications;
        this.properties = properties;
        this.self = self;
    }

    public DeadlineReminderResult sweep() {
        Instant now = Instant.now();
        Instant threshold = now.plus(REMINDER_WINDOW);
        List<Claim> candidates = claims.findNeedingDeadlineReminder(now, threshold);

        int sent = 0;
        int failed = 0;
        for (Claim claim : candidates) {
            try {
                self.markAndNotify(claim.getId());
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("寄送期限提醒失敗，略過繼續下一筆：claimId={}", claim.getId(), e);
            }
        }

        DeadlineReminderResult result = new DeadlineReminderResult(now, candidates.size(), sent, failed);
        if (result.hasWork()) {
            log.info("寄送期限提醒：掃到 {} 筆，成功寄出 {} 筆，失敗 {} 筆",
                    result.found(), result.sent(), result.failed());
        } else {
            log.debug("寄送期限提醒：沒有落在提醒窗口內的認領");
        }
        return result;
    }

    /**
     * 標記已寄出並通知捐贈者，兩件事包在同一個交易裡：不能標記了卻沒寄
     * （下次排程就會漏掉這筆），也不能寄了卻沒標記（下次排程會重複寄）。
     *
     * <p>{@code deadlineReminderSentAt} 的檢查是防呆：候選清單是 {@link #sweep()}
     * 查出來的，理論上不會有兩筆同時處理到同一筆認領，但每筆各自獨立交易、
     * 各自重新檢查一次防呆條件，比信任呼叫端「查過就一定還成立」更保守。
     */
    @Transactional
    void markAndNotify(UUID claimId) {
        Claim claim = claims.findWithDetailsById(claimId).orElseThrow();
        if (claim.getStatus() != ClaimStatus.CLAIMED || claim.getDeadlineReminderSentAt() != null) {
            return;
        }

        claim.markDeadlineReminderSent();

        String subject = "提醒：「%s」的寄送期限快到了".formatted(claim.getWish().getTitle());
        String body = """
                您好，

                您認領的「%s」寄送期限是 %s，記得盡快寄出並回來回報寄送資訊喔！

                查看認領詳情：
                %s/me/claims/%s

                線上聖誕老公公
                """.formatted(
                        claim.getWish().getTitle(),
                        NotificationFormat.dateTime(claim.getShipDeadlineAt()),
                        properties.publicUrl(),
                        claim.getId());
        notifications.send(claim.getDonor().getEmail(), subject, body);
    }
}
