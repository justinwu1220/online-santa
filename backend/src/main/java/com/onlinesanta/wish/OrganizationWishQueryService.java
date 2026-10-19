package com.onlinesanta.wish;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.attachment.AttachmentService;
import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.claim.dto.ClaimOrgView;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.message.MessageRepository;
import com.onlinesanta.wish.dto.OrganizationStageCounts;
import com.onlinesanta.wish.dto.PastClaimView;
import com.onlinesanta.wish.dto.WishOrgDetailView;
import com.onlinesanta.wish.dto.WishOrgView;

/**
 * 機構後台「願望管理」的合併讀取：一個願望加上它目前的認領，合成單一狀態（{@link WishStage}）。
 *
 * <p>資料表不合併——一個願望一生可能有多筆認領，各有歷程、對話與證明；這裡只是讀取時組合。
 */
@Service
public class OrganizationWishQueryService {

    private final WishService wishes;
    private final WishRepository wishRepository;
    private final ClaimRepository claims;
    private final MessageRepository messages;
    private final AttachmentService attachments;
    private final CurrentUserService currentUser;

    public OrganizationWishQueryService(WishService wishes, WishRepository wishRepository,
                                        ClaimRepository claims, MessageRepository messages,
                                        AttachmentService attachments, CurrentUserService currentUser) {
        this.wishes = wishes;
        this.wishRepository = wishRepository;
        this.claims = claims;
        this.messages = messages;
        this.attachments = attachments;
        this.currentUser = currentUser;
    }

    /**
     * 清單：每個願望帶合併狀態、目前認領摘要與未讀數。
     *
     * @param status 篩選用的願望狀態（設計稿的「已認領」就是 CLAIMED，細分在 stage）
     */
    @Transactional(readOnly = true)
    public Page<WishOrgView> list(WishStatus status, Integer year, Pageable pageable) {
        Page<Wish> page = wishes.listMine(status, year, pageable);
        List<UUID> wishIds = page.getContent().stream().map(Wish::getId).toList();

        Map<UUID, Claim> active = activeClaims(wishIds);
        Map<UUID, Long> unread = unreadCounts(wishIds);
        Map<UUID, String> imageUrls = attachments.wishImageUrls(wishIds);
        Map<UUID, List<AttachmentView>> letters = attachments.wishLetters(wishIds);
        Instant now = Instant.now();

        return page.map(wish -> WishOrgView.from(wish, imageUrls.get(wish.getId()),
                letters.getOrDefault(wish.getId(), List.of()),
                active.get(wish.getId()), unread.getOrDefault(wish.getId(), 0L), now));
    }

    /** 抽屜：願望、目前認領的完整資料、過往認領紀錄。別家機構的願望一律 404。 */
    @Transactional(readOnly = true)
    public WishOrgDetailView get(UUID wishId) {
        UUID organizationId = currentUser.requireOrganizationId();
        Wish wish = wishRepository.findWithOrganizationById(wishId)
                .orElseThrow(() -> ResourceNotFoundException.of("願望", wishId));
        if (!wish.getOrganization().getId().equals(organizationId)) {
            throw ResourceNotFoundException.of("願望", wishId);
        }

        Claim active = activeClaims(List.of(wishId)).get(wishId);
        Instant now = Instant.now();
        long unread = unreadCounts(List.of(wishId)).getOrDefault(wishId, 0L);

        WishOrgView view = WishOrgView.from(wish, attachments.wishImageUrl(wishId),
                attachments.wishLetters(wishId), active, unread, now);
        List<PastClaimView> past = claims.findByWishIdAndStatusInOrderByClaimedAtDesc(
                        wishId, EnumSet.of(ClaimStatus.RELEASED, ClaimStatus.CANCELLED))
                .stream().map(PastClaimView::from).toList();

        return new WishOrgDetailView(view, active == null ? null : ClaimOrgView.from(active), past);
    }

    /** 篩選列各項的數量，加上頂端待辦提示需要的細項。 */
    @Transactional(readOnly = true)
    public OrganizationStageCounts counts() {
        UUID organizationId = currentUser.requireOrganizationId();
        Map<WishStatus, Long> byStatus = new HashMap<>();
        for (Object[] row : wishRepository.countByOrganizationGroupedByStatus(organizationId)) {
            byStatus.put((WishStatus) row[0], (Long) row[1]);
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        long overdue = claims.countOverdueByOrganization(organizationId, Instant.now());
        long waitingOrOverdue = claims.countByOrganizationAndStatusIn(
                organizationId, EnumSet.of(ClaimStatus.CLAIMED));
        long shipped = claims.countByOrganizationAndStatusIn(
                organizationId, EnumSet.of(ClaimStatus.SHIPPED, ClaimStatus.RECEIVED));

        return new OrganizationStageCounts(
                total,
                byStatus.getOrDefault(WishStatus.DRAFT, 0L),
                byStatus.getOrDefault(WishStatus.PENDING_REVIEW, 0L),
                byStatus.getOrDefault(WishStatus.AVAILABLE, 0L),
                byStatus.getOrDefault(WishStatus.CLAIMED, 0L),
                byStatus.getOrDefault(WishStatus.FULFILLED, 0L),
                waitingOrOverdue - overdue,
                overdue,
                shipped);
    }

    private Map<UUID, Claim> activeClaims(List<UUID> wishIds) {
        if (wishIds.isEmpty()) {
            return Map.of();
        }
        return claims.findByWishIdInAndStatusIn(wishIds, ClaimStatus.occupyingStates()).stream()
                .collect(Collectors.toMap(claim -> claim.getWish().getId(), claim -> claim,
                        (first, second) -> first));
    }

    private Map<UUID, Long> unreadCounts(List<UUID> wishIds) {
        Map<UUID, Long> counts = new HashMap<>();
        if (wishIds.isEmpty()) {
            return counts;
        }
        UUID readerId = currentUser.require().userId();
        for (Object[] row : messages.countUnreadByWishIds(wishIds, readerId, ClaimStatus.occupyingStates())) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }
}
