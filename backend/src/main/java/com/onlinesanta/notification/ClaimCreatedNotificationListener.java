package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.event.ClaimCreatedEvent;
import com.onlinesanta.organization.Organization;

/**
 * 認領成立 → 兩邊都通知：機構的 contactEmail，以及認領者本人。
 *
 * <p>兩封信走同一個事件、同一次 {@code findWithDetailsById}，不拆成兩個監聽器——那會讓
 * 同一筆認領被 join fetch 查兩次，而兩封信的觸發條件完全相同。
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

    /**
     * {@code phase = AFTER_COMMIT}：一定要等認領成立的交易真的送出去才寄信，
     * 交易若因為某個原因回滾（例如樂觀鎖衝突），這裡完全不會被觸發——不會出現
     * 「信寄了但認領其實沒成立」的落差。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaimCreated(ClaimCreatedEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent(claim -> {
            notifyOrganization(claim);
            notifyDonor(claim);
        });
    }

    private void notifyOrganization(Claim claim) {
        Organization organization = claim.getWish().getOrganization();
        String subject = "「%s」有人認領了！".formatted(claim.getWish().getTitle());
        String body = """
                %s 您好，

                您的願望「%s」剛剛被認領了。

                認領時間：%s
                給孩子的話：%s

                請留意寄送期限，登入機構後台查看詳情、並與捐贈者保持聯繫：
                %s/org

                線上聖誕老人
                """.formatted(
                        organization.getName(),
                        claim.getWish().getTitle(),
                        NotificationFormat.dateTime(claim.getClaimedAt()),
                        claim.getDonorMessage() == null || claim.getDonorMessage().isBlank()
                                ? "（無）" : claim.getDonorMessage(),
                        properties.publicUrl());
        notifications.send(organization.getContactEmail(), subject, body);
    }

    /**
     * 認領者的確認信。
     *
     * <p>按下認領的當下，使用者就承諾了要在期限前買好並寄出——但在這之前他不會再收到
     * 任何訊息，下一次是期限快到的提醒信。把期限與後續步驟寫進信裡，他才有一封可以
     * 回頭翻的東西，而不必記得自己是在哪個網站上認領了什麼。
     */
    private void notifyDonor(Claim claim) {
        String title = claim.getWish().getTitle();
        String subject = "您已認領「%s」".formatted(title);
        String body = """
                您好，

                感謝您認領「%s」，這份心意會送到孩子手上。

                機構：%s
                寄送期限：%s

                接下來請在期限前把禮物寄出，然後回到認領頁回報寄送資訊。
                機構收到後會上傳收件證明，屆時我們會再通知您。

                認領頁：%s/me/claims/%s

                線上聖誕老人
                """.formatted(title,
                        claim.getWish().getOrganization().getName(),
                        NotificationFormat.dateTime(claim.getShipDeadlineAt()),
                        properties.publicUrl(), claim.getId());
        notifications.send(claim.getDonor().getEmail(), subject, body);
    }
}
