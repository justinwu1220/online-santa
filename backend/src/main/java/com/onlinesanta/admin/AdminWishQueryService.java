package com.onlinesanta.admin;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.admin.dto.AdminWishCounts;
import com.onlinesanta.admin.dto.AdminWishDetailView;
import com.onlinesanta.admin.dto.AdminWishListItem;
import com.onlinesanta.attachment.AttachmentService;
import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishEventService;
import com.onlinesanta.wish.WishRepository;
import com.onlinesanta.wish.WishStatus;

/**
 * 管理員「願望」頁的合併讀取：願望加上它目前的認領，篩選與搜尋（設計稿 16.3）。
 * 資料表不合併，這裡只是讀取時組合。草稿不給管理員看（還沒提交就不是平台的事）。
 */
@Service
public class AdminWishQueryService {

    /** 篩選列的七個項目。 */
    public enum Filter { ALL, REVIEW, OPEN, CLAIMED, LATE, DONE, FROZEN }

    private static final Set<WishStatus> NON_DRAFT = EnumSet.of(
            WishStatus.PENDING_REVIEW, WishStatus.AVAILABLE, WishStatus.CLAIMED, WishStatus.FULFILLED);

    private final WishRepository wishes;
    private final ClaimRepository claims;
    private final AttachmentService attachments;
    private final AdminAuditService audit;
    private final CurrentUserService currentUser;
    private final WishEventService history;
    private final com.onlinesanta.unlock.WishUnlockService unlocks;

    public AdminWishQueryService(WishRepository wishes, ClaimRepository claims,
                                 AttachmentService attachments, AdminAuditService audit,
                                 CurrentUserService currentUser, WishEventService history,
                                 com.onlinesanta.unlock.WishUnlockService unlocks) {
        this.wishes = wishes;
        this.claims = claims;
        this.attachments = attachments;
        this.audit = audit;
        this.currentUser = currentUser;
        this.history = history;
        this.unlocks = unlocks;
    }

    @Transactional(readOnly = true)
    public Page<AdminWishListItem> list(Filter filter, String query, Integer year, UUID organizationId, Pageable pageable) {
        currentUser.requireAdmin();
        Set<WishStatus> statuses = switch (filter) {
            case REVIEW -> EnumSet.of(WishStatus.PENDING_REVIEW);
            case OPEN -> EnumSet.of(WishStatus.AVAILABLE);
            case CLAIMED, LATE -> EnumSet.of(WishStatus.CLAIMED);
            case DONE -> EnumSet.of(WishStatus.FULFILLED);
            case ALL, FROZEN -> NON_DRAFT;
        };
        String q = query == null || query.isBlank() ? "" : "%" + query.trim().toLowerCase() + "%";
        Instant now = Instant.now();
        // 年度以 createdAt 的台北日曆年歸檔；沒選年度就涵蓋全部時間
        Instant from = year == null ? Instant.EPOCH : TaiwanYear.startOf(year);
        Instant to = year == null ? Instant.parse("3000-01-01T00:00:00Z") : TaiwanYear.endOf(year);
        Page<Wish> page = wishes.adminSearch(statuses, organizationId, filter == Filter.FROZEN,
                filter == Filter.LATE, now, from, to, q, pageable);

        List<UUID> ids = page.getContent().stream().map(Wish::getId).toList();
        Set<UUID> lateIds = lateWishIds(ids, now);
        Set<UUID> unlockIds = unlocks.wishIdsWithPending(ids);
        return page.map(wish -> AdminWishListItem.from(wish, lateIds.contains(wish.getId()),
                unlockIds.contains(wish.getId())));
    }

    @Transactional(readOnly = true)
    public AdminWishCounts counts(UUID organizationId) {
        currentUser.requireAdmin();
        long review = 0, open = 0, claimed = 0, done = 0;
        Instant now = Instant.now();
        List<Object[]> byStatus = organizationId == null
                ? wishes.countByStatus() : wishes.countByOrganizationGroupedByStatus(organizationId);
        for (Object[] row : byStatus) {
            WishStatus status = (WishStatus) row[0];
            long n = (Long) row[1];
            switch (status) {
                case PENDING_REVIEW -> review = n;
                case AVAILABLE -> open = n;
                case CLAIMED -> claimed = n;
                case FULFILLED -> done = n;
                default -> { }
            }
        }
        return new AdminWishCounts(review + open + claimed + done, review, open, claimed,
                organizationId == null ? claims.countOverdue(now) : claims.countOverdueByOrganization(organizationId, now),
                done,
                organizationId == null ? wishes.countFrozen() : wishes.countFrozenByOrganization(organizationId),
                unlocks.pendingCount());
    }

    /**
     * 抽屜資料。有認領者時會帶出捐贈人姓名與帳號信箱，所以寫一筆「查看認領詳情」稽核
     * （與舊的認領詳情頁一致）。價格也只在這裡出現。
     */
    @Transactional
    public AdminWishDetailView get(UUID wishId) {
        currentUser.requireAdmin();
        Wish wish = findWish(wishId);
        Claim active = claims.findByWishIdInAndStatusIn(List.of(wishId), ClaimStatus.occupyingStates())
                .stream().findFirst().orElse(null);
        if (active != null) {
            audit.record(AdminAuditAction.VIEW_CLAIM_DETAIL, active.getId(),
                    "%s / %s".formatted(wish.getOrganization().getName(), wish.getTitle()));
        }
        boolean late = active != null && active.isOverdue(Instant.now());
        boolean resubmission = wish.getStatus() == WishStatus.PENDING_REVIEW && wish.getPublishedAt() != null;
        var unlock = unlocks.pendingOf(wishId).orElse(null);
        return AdminWishDetailView.from(wish, active, late, attachments.wishLetters(wishId),
                resubmission ? history.changesSinceLastApproval(wish) : null, history.history(wishId),
                unlock, unlock == null ? List.of() : unlockChanges(wish, unlock.revision()));
    }

    /** 點擊載入：目前認領的寄件照片（含捐贈者個資）。寫稽核。孩子的手寫信件是公開的，在 {@link #get} 直接帶出。 */
    @Transactional
    public List<AttachmentView> files(UUID wishId) {
        currentUser.requireAdmin();
        Wish wish = findWish(wishId);
        audit.record(AdminAuditAction.VIEW_WISH_ATTACHMENTS, wishId, wish.getTitle());
        List<AttachmentView> result = new java.util.ArrayList<>();
        claims.findByWishIdInAndStatusIn(List.of(wishId), ClaimStatus.occupyingStates())
                .stream().findFirst()
                .ifPresent(claim -> result.addAll(attachments.claimAttachments(claim.getId())));
        return result;
    }

    /** 機構申請裡修改的內容與目前線上內容的差異。 */
    private static List<com.onlinesanta.wish.dto.WishChanges.FieldChange> unlockChanges(
            Wish wish, com.onlinesanta.unlock.WishRevision revision) {
        List<com.onlinesanta.wish.dto.WishChanges.FieldChange> changes = new java.util.ArrayList<>();
        if (revision == null) {
            return changes;
        }
        addChange(changes, "孩子的暱稱", wish.getChildAlias(), revision.childAlias());
        addChange(changes, "孩子的年齡", wish.getAgeRange() == null ? null : wish.getAgeRange().getLabel(),
                revision.ageRange() == null ? null : revision.ageRange().getLabel());
        addChange(changes, "願望描述", wish.getDescription(), revision.description());
        addChange(changes, "興趣", wish.getInterests(), revision.interests());
        return changes;
    }

    private static void addChange(List<com.onlinesanta.wish.dto.WishChanges.FieldChange> changes,
                                  String field, String before, String after) {
        if (after != null && !after.strip().equals(before == null ? "" : before.strip())) {
            changes.add(new com.onlinesanta.wish.dto.WishChanges.FieldChange(field, before == null ? "" : before, after));
        }
    }

    private Set<UUID> lateWishIds(List<UUID> wishIds, Instant now) {
        if (wishIds.isEmpty()) {
            return Set.of();
        }
        return claims.findByWishIdInAndStatusIn(wishIds, EnumSet.of(ClaimStatus.CLAIMED)).stream()
                .filter(claim -> claim.isOverdue(now))
                .map(claim -> claim.getWish().getId())
                .collect(Collectors.toSet());
    }

    private Wish findWish(UUID wishId) {
        return wishes.findWithOrganizationById(wishId)
                .filter(wish -> wish.getStatus() != WishStatus.DRAFT)
                .orElseThrow(() -> ResourceNotFoundException.of("願望", wishId));
    }
}
