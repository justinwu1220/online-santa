package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.event.ClaimAutoReleasedEvent;

/**
 * 逾期認領被系統自動收回後，通知原認領者（4.2）。
 *
 * <p>{@code AFTER_COMMIT}：收回排程整批是同一個交易，一筆失敗就全部回滾——回滾時認領根本沒有被收回，
 * 信就不該寄。信上的認領者讀自這筆認領本身，不是願望目前的狀態（願望可能已經被下一位認領）。
 */
@Component
public class ClaimAutoReleasedNotificationListener {

    private final ClaimRepository claims;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public ClaimAutoReleasedNotificationListener(ClaimRepository claims, NotificationService notifications,
                                                 NotificationProperties properties) {
        this.claims = claims;
        this.notifications = notifications;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaimAutoReleased(ClaimAutoReleasedEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent(claim ->
                notifications.sendTemplate(claim.getDonor().getEmail(), MailTemplateId.CLAIM_AUTO_RELEASED,
                        MailFields.of(
                                "recipientName", MailFields.recipientName(claim.getDonor()),
                                "wishTitle", claim.getWish().getTitle(),
                                "wishWallUrl", properties.publicUrl())));
    }
}
