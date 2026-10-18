package com.onlinesanta.admin;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.admin.dto.WishRejectRequest;
import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.event.WishReviewedEvent;
import com.onlinesanta.wish.Wish;
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

    public WishReviewService(WishRepository wishes, CurrentUserService currentUser,
                             AdminAuditService audit, ApplicationEventPublisher eventPublisher) {
        this.wishes = wishes;
        this.currentUser = currentUser;
        this.audit = audit;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Wish approve(UUID wishId) {
        AppPrincipal admin = currentUser.requireAdmin();
        Wish wish = find(wishId);
        wish.approve(admin.userId());
        audit.record(AdminAuditAction.APPROVE_WISH, wishId, wish.getTitle());
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
        eventPublisher.publishEvent(new WishReviewedEvent(wishId, false, reason));
        return wish;
    }

    private Wish find(UUID wishId) {
        return wishes.findWithOrganizationById(wishId)
                .orElseThrow(() -> ResourceNotFoundException.of("願望", wishId));
    }
}
