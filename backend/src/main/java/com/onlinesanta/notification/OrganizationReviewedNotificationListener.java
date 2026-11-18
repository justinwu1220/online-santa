package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.event.OrganizationReviewedEvent;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;

/** 機構申請審核結果（1.7）：通過、或需要補件。審核意見取自機構資料，空白時樣板整段省略。 */
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
        if (approved) {
            notifications.sendTemplate(organization.getContactEmail(), MailTemplateId.ORG_APPROVED, MailFields.of(
                    "organizationName", organization.getName(),
                    "reviewNote", organization.getReviewNote(),
                    "orgUrl", MailFields.orgUrl(properties)));
        } else {
            notifications.sendTemplate(organization.getContactEmail(), MailTemplateId.ORG_NEEDS_FIX, MailFields.of(
                    "organizationName", organization.getName(),
                    "reviewNote", organization.getReviewNote(),
                    "orgSettingsUrl", MailFields.orgSettingsUrl(properties)));
        }
    }
}
