package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.event.OrganizationSubmittedEvent;
import com.onlinesanta.event.OrganizationUnlockRejectedEvent;
import com.onlinesanta.event.WishModeratedByAdminEvent;
import com.onlinesanta.event.WishUnlockDecidedEvent;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishRepository;
import com.onlinesanta.wish.WishStatus;

/**
 * 寄給機構的處置通知：管理員處置願望（4.1）、願望解除凍結申請的結果（4.5）、
 * 解除停權申請被退回（4.9）、申請與重新送審的收件確認（4.10）。
 *
 * <p>全部 {@code AFTER_COMMIT} 並且<strong>在提交後重新讀取</strong>狀態：
 * 信上說的「仍凍結」「已回到願望牆」要是交易完成後的實際情況，而不是動作名稱暗示的情況
 * （例如核准了解除申請，願望卻可能因機構停權而仍然不公開）。
 */
@Component
public class OrganizationNoticeNotificationListener {

    static final String STILL_PAUSED = "這個願望目前仍暫停公開，請到後台查看原因。";

    private final WishRepository wishes;
    private final OrganizationRepository organizations;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public OrganizationNoticeNotificationListener(WishRepository wishes, OrganizationRepository organizations,
                                                  NotificationService notifications,
                                                  NotificationProperties properties) {
        this.wishes = wishes;
        this.organizations = organizations;
        this.notifications = notifications;
        this.properties = properties;
    }

    // ── 4.1 管理員處置願望 ───────────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWishModerated(WishModeratedByAdminEvent event) {
        wishes.findWithOrganizationById(event.wishId()).ifPresent(wish -> {
            Organization org = wish.getOrganization();
            var fields = MailFields.of(
                    "organizationName", org.getName(),
                    "wishTitle", wish.getTitle(),
                    "actionReason", event.reason(),
                    "orgUrl", MailFields.orgUrl(properties));
            switch (event.action()) {
                case FROZEN -> notifications.sendTemplate(org.getContactEmail(),
                        MailTemplateId.WISH_FROZEN_BY_ADMIN, fields);
                case CONTENT_EDITED -> notifications.sendTemplate(org.getContactEmail(),
                        MailTemplateId.WISH_EDITED_BY_ADMIN, MailFields.merge(fields,
                                MailFields.of("isFrozen", wish.isFrozen())));
                case PHOTO_REMOVED -> notifications.sendTemplate(org.getContactEmail(),
                        MailTemplateId.WISH_PHOTO_REMOVED_BY_ADMIN, MailFields.merge(fields,
                                MailFields.of("isFrozen", wish.isFrozen())));
                case UNFROZEN -> notifications.sendTemplate(org.getContactEmail(),
                        MailTemplateId.WISH_UNFROZEN_BY_ADMIN, MailFields.merge(fields,
                                MailFields.of("wishStatusText", wishStatusText(wish))));
            }
        });
    }

    // ── 4.5 願望解除凍結申請的結果 ────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnlockDecided(WishUnlockDecidedEvent event) {
        wishes.findWithOrganizationById(event.wishId()).ifPresent(wish -> {
            Organization org = wish.getOrganization();
            var fields = MailFields.of(
                    "organizationName", org.getName(),
                    "wishTitle", wish.getTitle(),
                    "reviewNote", event.note(),
                    "orgUrl", MailFields.orgUrl(properties));
            if (event.approved()) {
                notifications.sendTemplate(org.getContactEmail(), MailTemplateId.WISH_UNLOCK_APPROVED,
                        MailFields.merge(fields, MailFields.of("wishStatusText", wishStatusText(wish))));
            } else {
                notifications.sendTemplate(org.getContactEmail(), MailTemplateId.WISH_UNLOCK_NEEDS_INFO, fields);
            }
        });
    }

    // ── 4.9 解除停權申請被退回 ───────────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizationUnlockRejected(OrganizationUnlockRejectedEvent event) {
        organizations.findById(event.organizationId()).ifPresent(org ->
                notifications.sendTemplate(org.getContactEmail(), MailTemplateId.ORG_UNLOCK_REJECTED,
                        MailFields.of(
                                "organizationName", org.getName(),
                                "reviewNote", event.note(),
                                "orgUrl", MailFields.orgUrl(properties))));
    }

    // ── 4.10 申請與重新送審的收件確認 ─────────────────────────────
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizationSubmitted(OrganizationSubmittedEvent event) {
        organizations.findById(event.organizationId()).ifPresent(org -> {
            // 市話是選填；沒有市話就回報手機，讓機構核對留的聯絡電話對不對
            String phone = org.getContactPhone() != null && !org.getContactPhone().isBlank()
                    ? org.getContactPhone() : org.getMobilePhone();
            notifications.sendTemplate(org.getContactEmail(),
                    event.resubmission() ? MailTemplateId.ORG_RESUBMISSION_RECEIVED
                            : MailTemplateId.ORG_APPLICATION_RECEIVED,
                    MailFields.of(
                            "organizationName", org.getName(),
                            "contactEmail", org.getContactEmail(),
                            "contactPhone", phone));
        });
    }

    /**
     * 給機構看的願望目前狀態（一句話）。優先順序：仍有凍結 → 機構停權等造成的不公開 →
     * 可認領 → 已有認領者 → 其他階段。
     */
    static String wishStatusText(Wish wish) {
        if (wish.isFrozen()) {
            return STILL_PAUSED;
        }
        return switch (wish.getStatus()) {
            case AVAILABLE -> wish.isPubliclyVisible()
                    ? "這個願望已回到願望牆，開放認領。" : STILL_PAUSED;
            case CLAIMED -> "這個願望已有認領者，您可以在後台查看進度。";
            case PENDING_REVIEW -> "這個願望正在等待團隊審核。";
            case DRAFT -> "這個願望還是草稿，完成後請送審。";
            case FULFILLED -> "這個願望已經完成，謝謝您的協助。";
        };
    }
}
