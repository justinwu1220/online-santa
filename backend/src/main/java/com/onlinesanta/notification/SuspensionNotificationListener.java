package com.onlinesanta.notification;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.event.AffectedDonorNoticeEvent;
import com.onlinesanta.event.ClaimReleasedByAdminEvent;
import com.onlinesanta.event.OrgSuspensionClaimEvent;
import com.onlinesanta.event.OrganizationSuspensionEvent;
import com.onlinesanta.event.UserSuspensionEvent;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.user.UserRepository;

/**
 * 停權相關的通知：機構停權／解除（1.10）、受影響認領者暫停／恢復（1.11）、
 * 管理員手動的個案通知（1.12）、認領者帳號停權／恢復（1.13）、管理員結束認領（1.14）。
 *
 * <p>全部 {@code AFTER_COMMIT}：停權交易 rollback 的話，狀態根本沒有改，信不該寄。
 */
@Component
public class SuspensionNotificationListener {

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final ClaimRepository claims;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public SuspensionNotificationListener(OrganizationRepository organizations, UserRepository users,
                                          ClaimRepository claims,
                                          NotificationService notifications, NotificationProperties properties) {
        this.organizations = organizations;
        this.users = users;
        this.claims = claims;
        this.notifications = notifications;
        this.properties = properties;
    }

    // ── 1.10 機構停權／解除 ──────────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizationSuspension(OrganizationSuspensionEvent event) {
        organizations.findById(event.organizationId()).ifPresent(organization -> {
            if (event.suspended()) {
                notifications.sendTemplate(organization.getContactEmail(), MailTemplateId.ORG_SUSPENDED,
                        MailFields.of(
                                "organizationName", organization.getName(),
                                "suspensionReason", event.reason(),
                                "orgUrl", MailFields.orgUrl(properties)));
            } else {
                notifications.sendTemplate(organization.getContactEmail(), MailTemplateId.ORG_REINSTATED,
                        MailFields.of(
                                "organizationName", organization.getName(),
                                "orgUrl", MailFields.orgUrl(properties)));
            }
        });
    }

    // ── 1.11 機構停權／復權對認領者的影響 ────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaimAffected(OrgSuspensionClaimEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent(claim -> {
            var fields = MailFields.of(
                    "recipientName", MailFields.recipientName(claim.getDonor()),
                    "wishTitle", claim.getWish().getTitle(),
                    "organizationName", claim.getWish().getOrganization().getName(),
                    "claimUrl", MailFields.claimUrl(properties, claim));
            if (event.suspended()) {
                notifications.sendTemplate(claim.getDonor().getEmail(),
                        MailTemplateId.ORG_SUSPENDED_CLAIM_PAUSED, fields);
            } else {
                // 暫停時已逾期的認領，恢復後期限可能仍在過去：不顯示日期，見 MailFields.resumedDeadline
                notifications.sendTemplate(claim.getDonor().getEmail(),
                        MailTemplateId.ORG_REINSTATED_CLAIM_RESUMED,
                        MailFields.merge(fields, MailFields.resumedDeadline(claim, Instant.now())));
            }
        });
    }

    // ── 1.12 團隊給認領者的個案通知 ──────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAffectedDonorNotice(AffectedDonorNoticeEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent((Claim claim) -> notifications.sendTemplate(
                claim.getDonor().getEmail(), MailTemplateId.TEAM_CASE_NOTICE, MailFields.of(
                        "recipientName", MailFields.recipientName(claim.getDonor()),
                        "wishTitle", claim.getWish().getTitle(),
                        "organizationName", claim.getWish().getOrganization().getName(),
                        "adminMessage", event.message(),
                        "claimUrl", MailFields.claimUrl(properties, claim))));
    }

    // ── 1.13 認領者帳號停權／恢復 ────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserSuspension(UserSuspensionEvent event) {
        users.findById(event.userId()).ifPresent(user -> {
            if (event.suspended()) {
                notifications.sendTemplate(user.getEmail(), MailTemplateId.USER_SUSPENDED, MailFields.of(
                        "recipientName", MailFields.recipientName(user),
                        "suspensionReason", event.reason()));
            } else {
                notifications.sendTemplate(user.getEmail(), MailTemplateId.USER_REINSTATED, MailFields.of(
                        "recipientName", MailFields.recipientName(user),
                        "publicUrl", properties.publicUrl()));
            }
        });
    }

    // ── 1.14 管理員結束認領 ──────────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaimReleasedByAdmin(ClaimReleasedByAdminEvent event) {
        claims.findWithDetailsById(event.claimId()).ifPresent(claim -> notifications.sendTemplate(
                claim.getDonor().getEmail(), MailTemplateId.CLAIM_RELEASED_BY_ADMIN, MailFields.of(
                        "recipientName", MailFields.recipientName(claim.getDonor()),
                        "wishTitle", claim.getWish().getTitle(),
                        "releaseReason", event.reason())));
    }
}
