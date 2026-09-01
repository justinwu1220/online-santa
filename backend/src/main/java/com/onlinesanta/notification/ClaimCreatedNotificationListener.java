package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.event.ClaimCreatedEvent;
import com.onlinesanta.organization.Organization;

/** 認領成立 → 通知機構的 contactEmail。 */
@Component
public class ClaimCreatedNotificationListener {

    private final ClaimRepository claims;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public ClaimCreatedNotificationListener(ClaimRepository claims,
                                            NotificationService notifications,
                                            NotificationProperties properties) {
        this.claims = claims;
        this.notifications = notifications;
        this.properties = properties;
    }

    /**
     * {@code phase = AFTER_COMMIT}：一定要等認領成立的交易真的送出去才寄信，
     * 交易若因為某個原因回滾（例如樂觀鎖衝突），這裡完全不會被觸發——不會出現
     * 「信寄了但認領其實沒成立」的落差。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaimCreated(ClaimCreatedEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent(this::notify);
    }

    private void notify(Claim claim) {
        Organization organization = claim.getWish().getOrganization();
        String subject = "「%s」有人認領了！".formatted(claim.getWish().getTitle());
        String body = """
                %s 您好，

                您的願望「%s」剛剛被認領了。

                認領時間：%s
                給孩子的話：%s

                請留意寄送期限，登入機構後台查看詳情、並與捐贈者保持聯繫：
                %s/org/claims

                線上聖誕老公公
                """.formatted(
                        organization.getName(),
                        claim.getWish().getTitle(),
                        NotificationFormat.dateTime(claim.getClaimedAt()),
                        claim.getDonorMessage() == null || claim.getDonorMessage().isBlank()
                                ? "（無）" : claim.getDonorMessage(),
                        properties.publicUrl());
        notifications.send(organization.getContactEmail(), subject, body);
    }
}
