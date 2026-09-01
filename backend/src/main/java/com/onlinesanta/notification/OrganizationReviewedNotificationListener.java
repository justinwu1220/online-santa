package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.event.OrganizationReviewedEvent;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;

/** 機構審核結果（核准／駁回）→ 通知機構的 contactEmail。 */
@Component
public class OrganizationReviewedNotificationListener {

    private final OrganizationRepository organizations;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public OrganizationReviewedNotificationListener(OrganizationRepository organizations,
                                                     NotificationService notifications,
                                                     NotificationProperties properties) {
        this.organizations = organizations;
        this.notifications = notifications;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizationReviewed(OrganizationReviewedEvent event) {
        organizations.findById(event.organizationId()).ifPresent(
                organization -> notify(organization, event.approved()));
    }

    private void notify(Organization organization, boolean approved) {
        String subject = approved ? "機構審核結果：核准" : "機構審核結果：需要補件";
        String reasonLine = organization.getReviewNote() == null || organization.getReviewNote().isBlank()
                ? "" : "\n審核意見：" + organization.getReviewNote() + "\n";

        String body = approved
                ? """
                        %s 您好，

                        恭喜，您的機構申請已經核准，現在可以開始上架願望了。
                        %s
                        登入機構後台開始使用：
                        %s/org

                        線上聖誕老公公
                        """.formatted(organization.getName(), reasonLine, properties.publicUrl())
                : """
                        %s 您好，

                        您的機構申請這次沒有通過，請參考以下意見補件後重新送審。
                        %s
                        登入機構後台補件：
                        %s/org

                        線上聖誕老公公
                        """.formatted(organization.getName(), reasonLine, properties.publicUrl());

        notifications.send(organization.getContactEmail(), subject, body);
    }
}
