package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.event.FeedbackPhotoConfirmedEvent;

/**
 * 機構上傳收件證明照片後，通知認領者（1.5）。
 *
 * <p>每張確認的照片各寄一封（沒有節流），維持與先前相同的行為。
 */
@Component
public class FeedbackPhotoConfirmedNotificationListener {

    private final ClaimRepository claims;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public FeedbackPhotoConfirmedNotificationListener(ClaimRepository claims,
                                                       NotificationService notifications,
                                                       NotificationProperties properties) {
        this.claims = claims;
        this.notifications = notifications;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackPhotoConfirmed(FeedbackPhotoConfirmedEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent(this::notify);
    }

    private void notify(Claim claim) {
        notifications.sendTemplate(claim.getDonor().getEmail(), MailTemplateId.FEEDBACK_PHOTO, MailFields.of(
                "recipientName", MailFields.recipientName(claim.getDonor()),
                "wishTitle", claim.getWish().getTitle(),
                "organizationName", claim.getWish().getOrganization().getName(),
                "claimUrl", MailFields.claimUrl(properties, claim)));
    }
}
