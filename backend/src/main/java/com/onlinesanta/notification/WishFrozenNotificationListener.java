package com.onlinesanta.notification;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.event.WishFrozenEvent;

/**
 * 願望被管理員凍結或解凍時，通知該筆認領者暫停／恢復寄送（1.9）。
 *
 * <p>事件只在「確實有認領被暫停或恢復」時才發布，且恢復信的寄送期限讀的是交易提交後重新載入的
 * 認領——已經加回暫停時間。恢復後期限仍在過去的認領不顯示日期，見 {@link MailFields#resumedDeadline}。
 */
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
        // 「認領頁」要連到那一筆認領，不是網站首頁——首頁是願望牆，收信人看不到自己的認領
        String claimUrl = MailFields.claimUrl(properties, claim);
        String recipient = MailFields.recipientName(claim.getDonor());
        String title = claim.getWish().getTitle();
        if (frozen) {
            notifications.sendTemplate(claim.getDonor().getEmail(), MailTemplateId.WISH_PAUSED, MailFields.of(
                    "recipientName", recipient, "wishTitle", title, "claimUrl", claimUrl));
            return;
        }
        notifications.sendTemplate(claim.getDonor().getEmail(), MailTemplateId.WISH_RESUMED, MailFields.merge(
                MailFields.of(
                        "recipientName", recipient,
                        "wishTitle", title,
                        "childNickname", claim.getWish().getChildAlias(),
                        "childAge", claim.getWish().getAgeRange() == null
                                ? "—" : claim.getWish().getAgeRange().getLabel(),
                        "wishDescription", claim.getWish().getDescription() == null
                                ? "—" : claim.getWish().getDescription(),
                        "claimUrl", claimUrl),
                MailFields.resumedDeadline(claim, Instant.now())));
    }
}
