package com.onlinesanta.unlock;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditService;
import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.event.WishFrozenEvent;
import com.onlinesanta.message.SystemMessageService;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishEventService;
import com.onlinesanta.wish.WishEventType;
import com.onlinesanta.wish.WishFreeze;
import com.onlinesanta.wish.WishFreezeCause;
import com.onlinesanta.wish.WishFreezeService;
import com.onlinesanta.wish.WishRepository;

/**
 * 願望凍結的「申請解除」：機構看到管理員的凍結原因後，（內容疑慮時）修改內容並申請解除，
 * 管理員審核。機構的修改先存在申請裡，核准時才套用到線上內容——審核期間認領者看到的仍是
 * 管理員處理過的版本。
 *
 * <p>禮物（標題）不在修訂裡，見 {@link WishRevision}。機構只能對「管理員原因」的凍結申請，
 * 機構停權、認領者停權連動的凍結不歸這裡管。
 */
@Service
public class WishUnlockService {

    private final UnlockRequestRepository requests;
    private final WishRepository wishes;
    private final WishFreezeService freezes;
    private final WishEventService history;
    private final SystemMessageService systemMessages;
    private final CurrentUserService currentUser;
    private final AdminAuditService audit;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper json;

    public WishUnlockService(UnlockRequestRepository requests, WishRepository wishes, WishFreezeService freezes,
                             WishEventService history, SystemMessageService systemMessages,
                             CurrentUserService currentUser, AdminAuditService audit,
                             ApplicationEventPublisher eventPublisher, ObjectMapper json) {
        this.requests = requests;
        this.wishes = wishes;
        this.freezes = freezes;
        this.history = history;
        this.systemMessages = systemMessages;
        this.currentUser = currentUser;
        this.audit = audit;
        this.eventPublisher = eventPublisher;
        this.json = json;
    }

    // ---------------------------------------------------------------- 機構

    /** 機構申請解除凍結。內容疑慮（ADMIN_CONTENT）的凍結可以附上修改後的內容，其他管理員凍結只能直接申請。 */
    @Transactional
    public UnlockRequestView request(UUID wishId, String message, WishRevision revision) {
        AppPrincipal actor = currentUser.require();
        Wish wish = ownedWish(wishId);
        List<WishFreeze> active = freezes.activeFreezes(wishId);
        boolean adminFrozen = active.stream().anyMatch(f -> !f.getCause().isSystem());
        if (!adminFrozen) {
            throw new BusinessRuleException("WISH_NOT_UNLOCKABLE", active.isEmpty()
                    ? "這個願望目前沒有被凍結"
                    : "這個願望是因機構或認領者的停權而凍結，不能在願望上申請解除");
        }
        boolean hasRevision = revision != null && !revision.isEmpty();
        if (hasRevision && active.stream().noneMatch(f -> f.getCause() == WishFreezeCause.ADMIN_CONTENT)) {
            throw new BusinessRuleException("REVISION_NOT_ALLOWED",
                    "這次凍結不是因為內容疑慮，不能修改內容；請直接說明並申請解除");
        }
        if (requests.findFirstByTargetTypeAndTargetIdAndStatus(
                UnlockTargetType.WISH, wishId, UnlockStatus.PENDING).isPresent()) {
            throw new BusinessRuleException("UNLOCK_ALREADY_REQUESTED", "已經送出申請，請等管理員審核，或先撤回再重新申請");
        }

        UnlockRequest saved = requests.save(new UnlockRequest(UnlockTargetType.WISH, wishId,
                blankToNull(message), hasRevision ? write(revision) : null, actor.userId()));
        history.record(wish, WishEventType.UNLOCK_REQUESTED, actor.userId(), blankToNull(message), false);
        return view(saved);
    }

    /** 機構撤回還沒被審核的申請。 */
    @Transactional
    public UnlockRequestView withdraw(UUID wishId) {
        AppPrincipal actor = currentUser.require();
        Wish wish = ownedWish(wishId);
        UnlockRequest pending = pendingRequest(wishId);
        pending.decide(UnlockStatus.WITHDRAWN, actor.userId(), null);
        history.record(wish, WishEventType.UNLOCK_WITHDRAWN, actor.userId(), null, false);
        return view(pending);
    }

    /** 機構抽屜用：最近的一筆申請（待審或已決定），沒有為空。 */
    @Transactional(readOnly = true)
    public Optional<UnlockRequestView> latestOf(UUID wishId) {
        return requests.findFirstByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(UnlockTargetType.WISH, wishId)
                .map(this::view);
    }

    // ---------------------------------------------------------------- 管理員

    /** 管理員抽屜用：待審的申請，沒有為空。 */
    @Transactional(readOnly = true)
    public Optional<UnlockRequestView> pendingOf(UUID wishId) {
        return requests.findFirstByTargetTypeAndTargetIdAndStatus(
                UnlockTargetType.WISH, wishId, UnlockStatus.PENDING).map(this::view);
    }

    /** 這些願望之中有待審解除申請的 id（清單標示用）。 */
    @Transactional(readOnly = true)
    public Set<UUID> wishIdsWithPending(Collection<UUID> wishIds) {
        return wishIds.isEmpty() ? Set.of()
                : new HashSet<>(requests.findPendingTargetIds(UnlockTargetType.WISH, wishIds));
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return requests.countByTargetTypeAndStatus(UnlockTargetType.WISH, UnlockStatus.PENDING);
    }

    /** 核准：套用機構的修訂（有的話）、解除管理員的凍結、恢復期限，並通知認領者與機構。 */
    @Transactional
    public Wish approve(UUID wishId, String note) {
        return approve(wishId, note, false);
    }

    /** @param restoreLetter 信件照片目前被屏蔽時，是否一併恢復顯示（不恢復＝願望回牆但沒有信件） */
    @Transactional
    public Wish approve(UUID wishId, String note, boolean restoreLetter) {
        AppPrincipal admin = currentUser.requireAdmin();
        Wish wish = findWish(wishId);
        if (restoreLetter && wish.isLetterMasked()) {
            wish.restoreLetter();
        }
        UnlockRequest pending = pendingRequest(wishId);

        WishRevision revision = read(pending.getPayload());
        if (revision != null) {
            wish.applyContentFix(revision.childAlias(), revision.ageRange(), revision.interests(), revision.description());
        }
        Optional<Claim> resumed = freezes.liftAdminCauses(wish, admin.userId());
        pending.decide(UnlockStatus.APPROVED, admin.userId(), blankToNull(note));

        history.record(wish, WishEventType.UNLOCK_APPROVED, admin.userId(), blankToNull(note), revision != null);
        audit.record(AdminAuditAction.DECIDE_UNLOCK_REQUEST, wishId, "核准解除凍結：" + wish.getTitle());
        systemMessages.unlockDecided(wishId, true, note);
        resumed.ifPresent(claim -> eventPublisher.publishEvent(new WishFrozenEvent(claim.getId(), false)));
        return wish;
    }

    /** 退回：維持凍結，機構看得到原因，可以修改後再申請。 */
    @Transactional
    public Wish reject(UUID wishId, String note) {
        AppPrincipal admin = currentUser.requireAdmin();
        Wish wish = findWish(wishId);
        UnlockRequest pending = pendingRequest(wishId);
        pending.decide(UnlockStatus.REJECTED, admin.userId(), note.strip());
        history.record(wish, WishEventType.UNLOCK_REJECTED, admin.userId(), note.strip(), false);
        audit.record(AdminAuditAction.DECIDE_UNLOCK_REQUEST, wishId, "退回解除凍結申請：" + wish.getTitle());
        systemMessages.unlockDecided(wishId, false, note);
        return wish;
    }

    // ---------------------------------------------------------------- 內部

    private UnlockRequest pendingRequest(UUID wishId) {
        return requests.findFirstByTargetTypeAndTargetIdAndStatus(UnlockTargetType.WISH, wishId, UnlockStatus.PENDING)
                .orElseThrow(() -> new BusinessRuleException("NO_PENDING_UNLOCK_REQUEST", "這個願望目前沒有待審的解除申請"));
    }

    private Wish findWish(UUID wishId) {
        return wishes.findWithOrganizationById(wishId)
                .orElseThrow(() -> ResourceNotFoundException.of("願望", wishId));
    }

    private Wish ownedWish(UUID wishId) {
        UUID organizationId = currentUser.requireOrganizationId();
        Wish wish = findWish(wishId);
        if (!wish.getOrganization().getId().equals(organizationId)) {
            throw ResourceNotFoundException.of("願望", wishId);
        }
        return wish;
    }

    private UnlockRequestView view(UnlockRequest request) {
        return new UnlockRequestView(request.getId(), request.getStatus(), request.getMessage(),
                read(request.getPayload()), request.getDecisionNote(), request.getCreatedAt(), request.getDecidedAt());
    }

    private String write(WishRevision revision) {
        try {
            return json.writeValueAsString(revision);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("無法序列化願望修訂", e);
        }
    }

    private WishRevision read(String text) {
        if (text == null) {
            return null;
        }
        try {
            return json.readValue(text, WishRevision.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
