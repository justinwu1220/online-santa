package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimRestriction;
import com.onlinesanta.event.AffectedDonorNoticeEvent;
import com.onlinesanta.event.OrgSuspensionClaimEvent;
import com.onlinesanta.event.OrganizationSuspensionEvent;
import com.onlinesanta.organization.OrganizationRepository;

/**
 * 機構停權相關的通知：機構的聯絡信箱、還沒寄出的認領者（自動），以及管理員手動通知的認領者。
 * 都在交易提交後才寄。
 */
@Component
public class SuspensionNotificationListener {

    private final OrganizationRepository organizations;
    private final ClaimRepository claims;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public SuspensionNotificationListener(OrganizationRepository organizations, ClaimRepository claims,
                                          NotificationService notifications, NotificationProperties properties) {
        this.organizations = organizations;
        this.claims = claims;
        this.notifications = notifications;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizationSuspension(OrganizationSuspensionEvent event) {
        organizations.findById(event.organizationId()).ifPresent(organization -> {
            String subject = event.suspended() ? "您的機構已被停權" : "您的機構已解除停權";
            String body = event.suspended()
                    ? """
                    %s 您好，

                    平台管理員已停權貴機構，原因：%s

                    停權期間您仍可登入機構後台，在「與團隊對話」與管理員聯繫、修改機構資料並補上傳證明文件；
                    但無法對願望進行任何操作，也無法聯繫認領者。您的願望已凍結，認領者會看到停權說明。
                    資料處理好後，請到「機構設定」按「申請解除停權」。
                    %s/org

                    線上聖誕老人
                    """.formatted(organization.getName(), event.reason(), properties.publicUrl())
                    : """
                    %s 您好，

                    平台管理員已解除貴機構的停權，願望與認領已恢復，認領者的寄送期限會順延停權的時間。
                    %s/org

                    線上聖誕老人
                    """.formatted(organization.getName(), properties.publicUrl());
            notifications.send(organization.getContactEmail(), subject, body);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaimAffected(OrgSuspensionClaimEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent(claim -> {
            String title = claim.getWish().getTitle();
            String subject = event.suspended()
                    ? "您認領的「%s」暫停處理中".formatted(title)
                    : "您認領的「%s」已恢復處理".formatted(title);
            String body = event.suspended()
                    ? """
                    您好，

                    您認領的「%s」所屬機構已被管理員暫時停權，正在積極處理中。
                    暫停處理期間您不需要寄出禮物，寄送期限也暫停計算。若有問題可聯絡：%s
                    認領頁：%s

                    線上聖誕老人
                    """.formatted(title, ClaimRestriction.CONTACT_EMAIL, properties.publicUrl())
                    : """
                    您好，

                    您認領的「%s」所屬機構已恢復，暫停的時間會加回寄送期限，請到認領頁確認新的期限。
                    認領頁：%s

                    線上聖誕老人
                    """.formatted(title, properties.publicUrl());
            notifications.send(claim.getDonor().getEmail(), subject, body);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAffectedDonorNotice(AffectedDonorNoticeEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent((Claim claim) -> notifications.send(
                claim.getDonor().getEmail(),
                "關於您認領的「%s」".formatted(claim.getWish().getTitle()),
                """
                您好，

                關於您認領的「%s」（%s）：

                %s

                若有問題可聯絡：%s
                認領頁：%s

                線上聖誕老人
                """.formatted(claim.getWish().getTitle(), claim.getWish().getOrganization().getName(),
                        event.message(), ClaimRestriction.CONTACT_EMAIL, properties.publicUrl())));
    }
}
