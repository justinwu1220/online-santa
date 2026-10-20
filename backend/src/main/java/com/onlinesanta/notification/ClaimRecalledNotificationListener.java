package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.event.ClaimRecalledEvent;

/** 機構收回認領 → 寄信告知認領者原因，並說明不需要再寄送、可以認領其他願望。 */
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
        String subject = "機構收回了您認領的「%s」".formatted(claim.getWish().getTitle());
        String body = """
                您好，

                機構收回了您認領的「%s」。
                原因：%s

                您不需要再寄送禮物了，謝謝您的心意。也歡迎回到願望牆認領其他孩子的願望：
                %s

                線上聖誕老人
                """.formatted(claim.getWish().getTitle(), event.reason(), properties.publicUrl());
        notifications.send(claim.getDonor().getEmail(), subject, body);
    }
}
