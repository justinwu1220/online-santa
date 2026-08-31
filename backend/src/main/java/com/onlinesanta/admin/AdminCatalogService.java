package com.onlinesanta.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.attachment.AttachmentService;
import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimEvent;
import com.onlinesanta.claim.ClaimEventRepository;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishRepository;
import com.onlinesanta.wish.WishStatus;

/**
 * 管理員的跨機構唯讀檢視。
 *
 * <p>這是全系統權限最大的入口：看得到所有機構的孩童資料、所有捐贈者的個資，
 * 以及含孩童影像的回饋照片。因此**存取單一個人的資料時一律寫入稽核**。
 *
 * <p>清單頁不寫稽核——它顯示的是彙總與流程狀態，且每次翻頁都記會把真正重要的
 * 「某人打開了某個孩子的照片」淹沒在雜訊裡。
 *
 * <p>所有方法都是唯讀的。管理員不能從這裡改動任何資料，要介入只能透過機構審核。
 */
@Service
public class AdminCatalogService {

    private final WishRepository wishes;
    private final ClaimRepository claims;
    private final ClaimEventRepository claimEvents;
    private final AttachmentService attachments;
    private final AdminAuditService audit;

    public AdminCatalogService(WishRepository wishes,
                               ClaimRepository claims,
                               ClaimEventRepository claimEvents,
                               AttachmentService attachments,
                               AdminAuditService audit) {
        this.wishes = wishes;
        this.claims = claims;
        this.claimEvents = claimEvents;
        this.attachments = attachments;
        this.audit = audit;
    }

    // ---------------------------------------------------------------- 願望

    /**
     * @param year 選填的年度篩選（台北日曆年，以 {@code createdAt} 歸年——不可用
     *             {@code publishedAt}，理由同年度統計：已下架的願望重新上架時沿用第一次
     *             發布的時間戳）。null 代表不篩選
     */
    @Transactional(readOnly = true)
    public Page<Wish> listWishes(WishStatus status, Integer year, Pageable pageable) {
        if (year == null) {
            return status == null
                    ? wishes.findAllBy(pageable)
                    : wishes.findByStatus(status, pageable);
        }
        Instant from = TaiwanYear.startOf(year);
        Instant to = TaiwanYear.endOf(year);
        return status == null
                ? wishes.findByCreatedAtRange(from, to, pageable)
                : wishes.findByStatusAndCreatedAtRange(status, from, to, pageable);
    }

    // ---------------------------------------------------------------- 認領

    @Transactional(readOnly = true)
    public Page<Claim> listClaims(ClaimStatus status, boolean overdueOnly, Pageable pageable) {
        if (overdueOnly) {
            return claims.findAllOverdue(Instant.now(), pageable);
        }
        return status == null
                ? claims.findAllBy(pageable)
                : claims.findByStatus(status, pageable);
    }

    /**
     * 單筆認領的詳情。
     *
     * <p>會看到捐贈者的姓名與 email，因此寫入稽核。這不是 readOnly 的交易——
     * 稽核紀錄要跟著這次存取一起提交。
     */
    @Transactional
    public Claim getClaim(UUID claimId) {
        Claim claim = claims.findWithDetailsById(claimId)
                .orElseThrow(() -> ResourceNotFoundException.of("認領", claimId));

        audit.record(AdminAuditAction.VIEW_CLAIM_DETAIL, claimId,
                "%s / %s".formatted(claim.getWish().getOrganization().getName(),
                        claim.getWish().getTitle()));
        return claim;
    }

    @Transactional(readOnly = true)
    public List<ClaimEvent> timelineOf(UUID claimId) {
        return claimEvents.findByClaimIdOrderByCreatedAtAsc(claimId);
    }

    /**
     * 單筆認領的附件——包含寄送證明與可能含孩童影像的回饋照片。
     *
     * <p>這是整個系統中最敏感的存取，稽核紀錄的 detail 會記下實際看到幾個檔案。
     */
    @Transactional
    public List<AttachmentView> attachmentsOf(UUID claimId) {
        Claim claim = claims.findWithDetailsById(claimId)
                .orElseThrow(() -> ResourceNotFoundException.of("認領", claimId));

        List<AttachmentView> views = attachments.claimAttachments(claimId);
        audit.record(AdminAuditAction.VIEW_CLAIM_ATTACHMENTS, claimId,
                "%s / %s，共 %d 個檔案".formatted(
                        claim.getWish().getOrganization().getName(),
                        claim.getWish().getTitle(),
                        views.size()));
        return views;
    }
}
