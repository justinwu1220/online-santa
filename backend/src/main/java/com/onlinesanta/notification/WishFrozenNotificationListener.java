package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimRestriction;
import com.onlinesanta.event.WishFrozenEvent;

/** 管理員凍結／解除凍結已認領的願望 → 寄信告知認領者：暫停期間不用寄出，解除後期限順延。 */
@Component
public class WishFrozenNotificationListener {

    private final ClaimRepository claims;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public WishFrozenNotificationListener(ClaimRepository claims, NotificationService notifications,
                                          NotificationProperties properties) {
        this.claims = claims;
        this.notifications = notifications;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWishFrozen(WishFrozenEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent(claim -> notify(claim, event.frozen()));
    }

    private void notify(Claim claim, boolean frozen) {
        String title = claim.getWish().getTitle();
        // 「認領頁」要連到那一筆認領，不是網站首頁——首頁是願望牆，收信人看不到自己的認領
        String claimUrl = properties.publicUrl() + "/me/claims/" + claim.getId();
        String subject = frozen
                ? "您認領的「%s」暫停處理中".formatted(title)
                : "您認領的「%s」已恢復處理".formatted(title);
        String body = frozen
                ? """
                您好，

                平台正在確認您認領的「%s」的內容，暫停處理期間您不需要寄出禮物，寄送期限也會暫停計算。
                如有疑問，歡迎在認領頁向機構詢問，或來信 %s。
                認領頁：%s

                線上聖誕老人
                """.formatted(title, ClaimRestriction.CONTACT_EMAIL, claimUrl)
                : """
                您好，

                您認領的「%s」已經恢復處理，暫停的時間會加回寄送期限，請到認領頁確認新的期限。
                願望內容可能已經更新，目前的內容如下：
                  孩子的暱稱：%s
                  年齡：%s
                  願望說明：%s
                認領頁：%s

                線上聖誕老人
                """.formatted(title, claim.getWish().getChildAlias(),
                        claim.getWish().getAgeRange() == null ? "—" : claim.getWish().getAgeRange().getLabel(),
                        claim.getWish().getDescription() == null ? "—" : claim.getWish().getDescription(),
                        claimUrl);
        notifications.send(claim.getDonor().getEmail(), subject, body);
    }
}
