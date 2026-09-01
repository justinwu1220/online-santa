package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.event.FeedbackPhotoConfirmedEvent;

/** 機構的送禮回饋照片確認上傳成功 → 通知該筆認領的捐贈者。 */
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
        String subject = "「%s」的機構回饋照片來囉".formatted(claim.getWish().getTitle());
        String body = """
                您好，

                您送出的「%s」，機構上傳了送禮回饋照片，一起看看孩子收到禮物的樣子吧：
                %s/me/claims/%s

                線上聖誕老公公
                """.formatted(claim.getWish().getTitle(), properties.publicUrl(), claim.getId());
        notifications.send(claim.getDonor().getEmail(), subject, body);
    }
}
