package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.event.ClaimCreatedEvent;

/**
 * 認領成立後寄「認領確認信」（1.2）給認領者。
 *
 * <p>不再寄信給機構（1.1）：機構透過後台與站內訊息得知有人認領，不需要每次認領都收一封信。
 *
 * <p>{@code AFTER_COMMIT}：認領交易 rollback 的話，認領根本沒有成立，信就不該寄。
 */
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaimCreated(ClaimCreatedEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent(this::notifyDonor);
    }

    private void notifyDonor(Claim claim) {
        notifications.sendTemplate(claim.getDonor().getEmail(), MailTemplateId.CLAIM_CONFIRMED, MailFields.of(
                "recipientName", MailFields.recipientName(claim.getDonor()),
                "wishTitle", claim.getWish().getTitle(),
                "organizationName", claim.getWish().getOrganization().getName(),
                "shipDeadline", NotificationFormat.dateTime(claim.getShipDeadlineAt()),
                "claimUrl", MailFields.claimUrl(properties, claim)));
    }
}
