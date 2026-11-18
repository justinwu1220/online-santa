package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.event.ClaimRecalledEvent;

/** 機構收回認領後，通知原認領者不用再寄禮物了（1.4）。 */
@Component
public class ClaimRecalledNotificationListener {

    private final ClaimRepository claims;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public ClaimRecalledNotificationListener(ClaimRepository claims, NotificationService notifications,
                                             NotificationProperties properties) {
        this.claims = claims;
        this.notifications = notifications;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaimRecalled(ClaimRecalledEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent(claim -> notify(claim, event));
    }

    private void notify(Claim claim, ClaimRecalledEvent event) {
        notifications.sendTemplate(claim.getDonor().getEmail(), MailTemplateId.CLAIM_RECALLED, MailFields.of(
                "recipientName", MailFields.recipientName(claim.getDonor()),
                "wishTitle", claim.getWish().getTitle(),
                "organizationName", claim.getWish().getOrganization().getName(),
                "recallReason", event.reason(),
                "wishWallUrl", properties.publicUrl()));
    }
}
