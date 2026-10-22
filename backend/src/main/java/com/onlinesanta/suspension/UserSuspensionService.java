package com.onlinesanta.suspension;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditService;
import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimRestriction;
import com.onlinesanta.claim.ClaimService;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.event.UserSuspensionEvent;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishFreezeCause;
import com.onlinesanta.wish.WishFreezeService;
import com.onlinesanta.wish.WishRepository;

/**
 * 認領者違規的處置：停權（受限登入）、解除停權、管理員釋出認領。
 *
 * <p><strong>停權</strong>：保全認領者資訊快照 → 帳號進入受限狀態（可登入看停權通知，所有寫入動作被擋，
 * 見 {@code SuspendedUserInterceptor}）→ 他進行中的認領的願望加上 {@code DONOR_SUSPENDED} 凍結
 * （還沒寄出的暫停寄送期限）→ Email 通知本人。機構後台看到「認領者已凍結」。
 *
 * <p><strong>解除停權</strong>只解除「認領者停權」這個凍結原因，其他原因的凍結保留。
 * 管理員也可以對還沒寄出的認領按「釋出」，認領結束、願望回到願望牆（見 {@link ClaimService#releaseByAdmin}）。
 */
@Service
public class UserSuspensionService {

    private static final EnumSet<ClaimStatus> ACTIVE = EnumSet.of(
            ClaimStatus.CLAIMED, ClaimStatus.SHIPPED, ClaimStatus.RECEIVED);

    private final UserRepository users;
    private final ClaimRepository claims;
    private final ClaimService claimService;
    private final WishRepository wishes;
    private final WishFreezeService freezes;
    private final SuspensionRecordRepository records;
    private final AdminAuditService audit;
    private final CurrentUserService currentUser;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper json;

    public UserSuspensionService(UserRepository users, ClaimRepository claims, ClaimService claimService,
                                 WishRepository wishes, WishFreezeService freezes,
                                 SuspensionRecordRepository records, AdminAuditService audit,
                                 CurrentUserService currentUser, ApplicationEventPublisher eventPublisher,
                                 ObjectMapper json) {
        this.users = users;
        this.claims = claims;
        this.claimService = claimService;
        this.wishes = wishes;
        this.freezes = freezes;
        this.records = records;
        this.audit = audit;
        this.currentUser = currentUser;
        this.eventPublisher = eventPublisher;
        this.json = json;
    }

    // ---------------------------------------------------------------- 查詢

    /** 認領者清單（依信箱、顯示名稱或真實姓名搜尋）。管理員專用。 */
    @Transactional(readOnly = true)
    public Page<AdminUserView> search(String query, Pageable pageable) {
        currentUser.requireAdmin();
        String q = query == null || query.isBlank() ? "" : "%" + query.trim().toLowerCase() + "%";
        return users.searchDonors(q, pageable).map(AdminUserView::from);
    }

    /** 單一認領者的處置資料：真實姓名等個資與進行中的認領，每次查看寫稽核。 */
    @Transactional
    public AdminUserDetailView detailOf(UUID userId) {
        currentUser.requireAdmin();
        User user = donor(userId);
        audit.record(AdminAuditAction.VIEW_SUSPENSION_RECORD, userId, user.getEmail());
        List<AdminUserDetailView.ClaimItem> items = claims.findByDonorIdAndStatusIn(userId, ACTIVE).stream()
                .map(claim -> new AdminUserDetailView.ClaimItem(
                        claim.getId(), claim.getWish().getId(), claim.getWish().getTitle(),
                        claim.getWish().getOrganization().getName(), claim.getStatus(),
                        ClaimRestriction.of(claim).name(), claim.getClaimedAt(),
                        claim.getStatus() == ClaimStatus.CLAIMED))
                .toList();
        JsonNode snapshot = records
                .findFirstByTargetTypeAndTargetIdAndLiftedAtIsNullOrderByCreatedAtDescIdDesc(
                        SuspensionTargetType.USER, userId)
                .map(record -> read(record.getSnapshot())).orElse(null);
        return new AdminUserDetailView(AdminUserView.from(user), user.getPhone(), items, snapshot);
    }

    // ---------------------------------------------------------------- 處置

    /** 停權。只有一般認領者（DONOR）能被停權，已經停權的不能重複停權。 */
    @Transactional
    public AdminUserView suspend(UUID userId, String reason) {
        AppPrincipal admin = currentUser.requireAdmin();
        User user = donor(userId);
        if (user.isSuspended()) {
            throw new BusinessRuleException("USER_ALREADY_SUSPENDED", "這位認領者已經被停權了");
        }
        String text = reason.strip();
        List<Claim> active = claims.findByDonorIdAndStatusIn(userId, ACTIVE);

        // 先保全證據，再改狀態：快照一定是停權當下的樣子
        records.save(new SuspensionRecord(SuspensionTargetType.USER, userId, text,
                write(snapshotOf(user, active)), admin.userId()));
        user.suspend(admin.userId(), text);
        audit.record(AdminAuditAction.SUSPEND_USER, userId, "%s，理由：%s".formatted(user.getEmail(), text));

        for (Claim claim : active) {
            freezes.freeze(claim.getWish(), WishFreezeCause.DONOR_SUSPENDED, "認領者出現違規行為", admin.userId());
        }
        eventPublisher.publishEvent(new UserSuspensionEvent(userId, true, text));
        return AdminUserView.from(user);
    }

    /** 解除停權：功能恢復；因停權而凍結的願望解除（其他原因的凍結保留），認領者的寄送期限順延。 */
    @Transactional
    public AdminUserView reinstate(UUID userId, String note) {
        AppPrincipal admin = currentUser.requireAdmin();
        User user = donor(userId);
        if (!user.isSuspended()) {
            throw new BusinessRuleException("USER_NOT_SUSPENDED", "這位認領者目前沒有被停權");
        }
        user.reinstate();
        audit.record(AdminAuditAction.REACTIVATE_USER, userId,
                note == null || note.isBlank() ? user.getEmail() : "%s，附註：%s".formatted(user.getEmail(), note.strip()));
        records.findFirstByTargetTypeAndTargetIdAndLiftedAtIsNullOrderByCreatedAtDescIdDesc(
                SuspensionTargetType.USER, userId).ifPresent(record -> record.lift(admin.userId()));

        for (Claim claim : claims.findByDonorIdAndStatusIn(userId, ACTIVE)) {
            freezes.liftCause(claim.getWish(), WishFreezeCause.DONOR_SUSPENDED, admin.userId());
        }
        eventPublisher.publishEvent(new UserSuspensionEvent(userId, false, null));
        return AdminUserView.from(user);
    }

    /**
     * 管理員釋出一筆還沒寄出的認領，讓願望重新被認領。釋出後解除這筆認領因「認領者停權」而加上的凍結
     * （願望上其他原因的凍結保留）。
     */
    @Transactional
    public void releaseClaim(UUID claimId, String reason) {
        currentUser.requireAdmin();
        Claim released = claimService.releaseByAdmin(claimId, reason);
        UUID wishId = released.getWish().getId();
        audit.record(AdminAuditAction.RELEASE_CLAIM_BY_ADMIN, claimId, "管理員釋出認領，原因：" + reason.strip());
        // 釋出時的批次更新會清掉持久化脈絡，重新載入願望再解除凍結
        Optional<Wish> wish = wishes.findWithOrganizationById(wishId);
        wish.ifPresent(w -> freezes.liftCause(w, WishFreezeCause.DONOR_SUSPENDED, currentUser.require().userId()));
    }

    // ---------------------------------------------------------------- 內部

    private User donor(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("使用者", userId));
        if (user.getRole() != UserRole.DONOR) {
            // 機構成員與管理員不走認領者停權：機構問題用機構停權，管理員由白名單管理
            throw ResourceNotFoundException.of("使用者", userId);
        }
        return user;
    }

    /** 停權當下的認領者資訊：個資與進行中的認領清單。 */
    private Map<String, Object> snapshotOf(User user, List<Claim> active) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("email", user.getEmail());
        snapshot.put("displayName", user.getDisplayName());
        snapshot.put("name", user.getName());
        snapshot.put("phone", user.getPhone());
        snapshot.put("claims", active.stream().map(claim -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("claimId", claim.getId().toString());
            item.put("wishTitle", claim.getWish().getTitle());
            item.put("organization", claim.getWish().getOrganization().getName());
            item.put("status", claim.getStatus().name());
            return item;
        }).toList());
        return snapshot;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("無法序列化停權快照", e);
        }
    }

    private JsonNode read(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
