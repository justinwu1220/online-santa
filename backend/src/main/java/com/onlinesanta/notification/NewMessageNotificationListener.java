package com.onlinesanta.notification;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.event.NewMessageEvent;
import com.onlinesanta.organization.Organization;

/**
 * 新的站內訊息 → 通知對方。捐贈者發送時通知機構的 contactEmail，
 * 機構發送時通知捐贈者。防轟炸的節流判斷在 {@code MessageService.send} 就做完了，
 * 這裡收到事件一律寄信。
 */
@Component
public class NewMessageNotificationListener {

    private final ClaimRepository claims;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public NewMessageNotificationListener(ClaimRepository claims,
                                          NotificationService notifications,
                                          NotificationProperties properties) {
        this.claims = claims;
        this.notifications = notifications;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewMessage(NewMessageEvent event) {
        claims.findWithDetailsById(event.claimId())
                .ifPresent(claim -> notify(claim, event.senderUserId()));
    }

    private void notify(Claim claim, UUID senderUserId) {
        String subject = "「%s」有新訊息".formatted(claim.getWish().getTitle());
        boolean senderIsDonor = claim.isOwnedBy(senderUserId);

        String to;
        String link;
        if (senderIsDonor) {
            Organization organization = claim.getWish().getOrganization();
            to = organization.getContactEmail();
            link = properties.publicUrl() + "/org/claims";
        } else {
            to = claim.getDonor().getEmail();
            link = properties.publicUrl() + "/me/claims/" + claim.getId();
        }

        String body = """
                您好，

                「%s」這筆認領有新的訊息，登入查看並回覆：
                %s

                線上聖誕老公公
                """.formatted(claim.getWish().getTitle(), link);
        notifications.send(to, subject, body);
    }
}
