package com.onlinesanta.admin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.admin.dto.WishRejectRequest;
import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.event.WishFrozenEvent;
import com.onlinesanta.event.WishReviewedEvent;
import com.onlinesanta.message.SystemMessageService;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishEventService;
import com.onlinesanta.wish.WishEventType;
import com.onlinesanta.wish.WishFreezeCause;
import com.onlinesanta.wish.WishFreezeService;
import com.onlinesanta.wish.WishRepository;

/**
 * 平台管理員的願望審核：機構提交的願望要經過核准才會公開在願望牆。
 *
 * <p>審核是孩童資料公開前的最後一道人工把關（暱稱、信件照片有沒有洩漏可識別資訊）。
 * 核准與退回都寫稽核紀錄，並發出事件，讓該願望的對話留下系統訊息、通知機構。
 */
@Service
public class WishReviewService {

    private final WishRepository wishes;
    private final CurrentUserService currentUser;
    private final AdminAuditService audit;
    private final ApplicationEventPublisher eventPublisher;
    private final SystemMessageService systemMessages;
    private final WishEventService history;
    private final WishFreezeService freezes;

    public WishReviewService(WishRepository wishes, CurrentUserService currentUser,
                             AdminAuditService audit, ApplicationEventPublisher eventPublisher,
                             SystemMessageService systemMessages, WishEventService history,
                             WishFreezeService freezes) {
        this.wishes = wishes;
        this.currentUser = currentUser;
        this.audit = audit;
        this.eventPublisher = eventPublisher;
        this.systemMessages = systemMessages;
        this.history = history;
        this.freezes = freezes;
    }

    @Transactional
    public Wish approve(UUID wishId) {
        AppPrincipal admin = currentUser.requireAdmin();
        Wish wish = find(wishId);
        wish.approve(admin.userId());
        audit.record(AdminAuditAction.APPROVE_WISH, wishId, wish.getTitle());
        history.record(wish, WishEventType.APPROVED, admin.userId(), null, true);
        systemMessages.wishApproved(wishId);
        eventPublisher.publishEvent(new WishReviewedEvent(wishId, true, null));
        return wish;
    }

    @Transactional
    public Wish reject(UUID wishId, WishRejectRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        Wish wish = find(wishId);
        String reason = request.note().trim();
        wish.reject(admin.userId(), reason);
        audit.record(AdminAuditAction.REJECT_WISH, wishId, wish.getTitle());
        history.record(wish, WishEventType.REJECTED, admin.userId(), reason, false);
        systemMessages.wishRejected(wishId, reason);
        eventPublisher.publishEvent(new WishReviewedEvent(wishId, false, reason));
        return wish;
    }

    /**
     * 暫時凍結：理由必填，稽核並通知機構。凍結只影響對外顯示，不動願望與認領的狀態；
     * 已認領的願望，認領者仍看得到自己的認領（顯示「暫停處理中」），寄送期限暫停計算並寄信告知。
     * 多個原因的疊加與期限暫停見 {@link WishFreezeService}。
     */
    @Transactional
    public Wish freeze(UUID wishId, com.onlinesanta.admin.dto.WishFreezeRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        Wish wish = find(wishId);
        String reason = request.note().trim();
        Optional<Claim> paused = freezes.freeze(wish, WishFreezeCause.ADMIN_OTHER, reason, admin.userId());
        audit.record(AdminAuditAction.FREEZE_WISH, wishId, wish.getTitle());
        history.record(wish, WishEventType.FROZEN, admin.userId(), reason, false);
        systemMessages.wishFrozen(wishId, reason);
        paused.ifPresent(claim -> eventPublisher.publishEvent(new WishFrozenEvent(claim.getId(), true)));
        return wish;
    }

    /**
     * 個資外洩的處置：管理員直接改暱稱、年齡、興趣、說明（不含禮物），並凍結。
     *
     * <p>同時把這個願望歷史快照裡被改的欄位遮蔽掉，避免外洩的內容留在歷程與「舊→新」比對裡。
     * 稽核與歷程只記錄「改了哪些欄位」與原因，不記內容本身。已經被管理員凍結的願望只改內容、不重複凍結。
     */
    @Transactional
    public Wish editContent(UUID wishId, com.onlinesanta.admin.dto.WishContentEditRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        Wish wish = find(wishId);
        wish.requireFreezable();
        String reason = request.reason().trim();

        java.util.Set<String> changed = wish.applyContentFix(
                request.childAlias(), request.ageRange(), request.interests(), request.description());
        if (changed.isEmpty() && !request.shouldFreeze()) {
            throw new com.onlinesanta.common.exception.BusinessRuleException("NO_CONTENT_CHANGES", "沒有任何欄位被修改");
        }
        history.redactSnapshots(wishId, changed);
        history.record(wish, WishEventType.ADMIN_EDITED, admin.userId(),
                "原因：%s；修改欄位：%s".formatted(reason, changed.isEmpty() ? "（無）" : String.join("、", changed)), true);
        audit.record(AdminAuditAction.EDIT_WISH_CONTENT, wishId,
                "%s；修改欄位：%s".formatted(wish.getTitle(), changed.isEmpty() ? "（無）" : String.join("、", changed)));

        Optional<Claim> paused = Optional.empty();
        if (request.shouldFreeze()) {
            try {
                paused = freezes.freeze(wish, WishFreezeCause.ADMIN_CONTENT, reason, admin.userId());
                history.record(wish, WishEventType.FROZEN, admin.userId(), reason, false);
            } catch (com.onlinesanta.common.exception.BusinessRuleException e) {
                if (!"WISH_ALREADY_FROZEN".equals(e.getErrorCode())) {
                    throw e;
                }
            }
        }
        systemMessages.wishContentEdited(wishId, reason);
        paused.ifPresent(claim -> eventPublisher.publishEvent(new WishFrozenEvent(claim.getId(), true)));
        return wish;
    }

    @Transactional
    public Wish unfreeze(UUID wishId) {
        AppPrincipal admin = currentUser.requireAdmin();
        Wish wish = find(wishId);
        Optional<Claim> resumed = freezes.liftAdminCauses(wish, admin.userId());
        audit.record(AdminAuditAction.UNFREEZE_WISH, wishId, wish.getTitle());
        history.record(wish, WishEventType.UNFROZEN, admin.userId(), null, false);
        systemMessages.wishUnfrozen(wishId);
        resumed.ifPresent(claim -> eventPublisher.publishEvent(new WishFrozenEvent(claim.getId(), false)));
        return wish;
    }

    private Wish find(UUID wishId) {
        return wishes.findWithOrganizationById(wishId)
                .orElseThrow(() -> ResourceNotFoundException.of("願望", wishId));
    }
}
