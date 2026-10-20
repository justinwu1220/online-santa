package com.onlinesanta.admin;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.admin.dto.WishRejectRequest;
import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.event.WishFrozenEvent;
import com.onlinesanta.event.WishReviewedEvent;
import com.onlinesanta.message.SystemMessageService;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishEventService;
import com.onlinesanta.wish.WishEventType;
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
    private final ClaimRepository claims;

    public WishReviewService(WishRepository wishes, CurrentUserService currentUser,
                             AdminAuditService audit, ApplicationEventPublisher eventPublisher,
                             SystemMessageService systemMessages, WishEventService history,
                             ClaimRepository claims) {
        this.wishes = wishes;
        this.currentUser = currentUser;
        this.audit = audit;
        this.eventPublisher = eventPublisher;
        this.systemMessages = systemMessages;
        this.history = history;
        this.claims = claims;
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
     */
    @Transactional
    public Wish freeze(UUID wishId, com.onlinesanta.admin.dto.WishFreezeRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        Wish wish = find(wishId);
        String reason = request.note().trim();
        wish.freeze(admin.userId(), reason);
        audit.record(AdminAuditAction.FREEZE_WISH, wishId, wish.getTitle());
        history.record(wish, WishEventType.FROZEN, admin.userId(), reason, false);
        systemMessages.wishFrozen(wishId, reason);
        // 只有還沒寄出的認領有期限與寄送義務可暫停、需要通知；寄出之後的認領不受凍結影響
        activeClaim(wishId).filter(claim -> claim.getStatus() == ClaimStatus.CLAIMED).ifPresent(claim -> {
            claim.pauseDeadline(Instant.now());
            eventPublisher.publishEvent(new WishFrozenEvent(claim.getId(), true));
        });
        return wish;
    }

    @Transactional
    public Wish unfreeze(UUID wishId) {
        AppPrincipal admin = currentUser.requireAdmin();
        Wish wish = find(wishId);
        wish.unfreeze();
        audit.record(AdminAuditAction.UNFREEZE_WISH, wishId, wish.getTitle());
        history.record(wish, WishEventType.UNFROZEN, admin.userId(), null, false);
        systemMessages.wishUnfrozen(wishId);
        activeClaim(wishId).filter(claim -> claim.getStatus() == ClaimStatus.CLAIMED).ifPresent(claim -> {
            claim.resumeDeadline(Instant.now());
            eventPublisher.publishEvent(new WishFrozenEvent(claim.getId(), false));
        });
        return wish;
    }

    /** 這個願望目前進行中（還沒完成）的認領；沒有認領的願望凍結不需要通知任何人。 */
    private Optional<Claim> activeClaim(UUID wishId) {
        return claims.findFirstByWishIdAndStatusIn(wishId,
                EnumSet.of(ClaimStatus.CLAIMED, ClaimStatus.SHIPPED, ClaimStatus.RECEIVED));
    }

    private Wish find(UUID wishId) {
        return wishes.findWithOrganizationById(wishId)
                .orElseThrow(() -> ResourceNotFoundException.of("願望", wishId));
    }
}
