package com.onlinesanta.wish;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.common.exception.BusinessRuleException;

/**
 * 願望凍結的唯一入口：管理員凍結、機構停權連動、認領者停權連動都走這裡。
 *
 * <p>凍結是多原因的：每個原因在 {@code wish_freezes} 各有一筆紀錄，只有<strong>所有原因都解除</strong>，
 * 願望才真的解除凍結、還沒寄出的認領才恢復寄送期限。這樣機構復權時不會把另一個個資凍結一起解掉。
 * 願望上的 {@code frozen_*} 欄位是反正規化的結果（願望牆、{@code isFrozen()} 都用它），只在這裡寫。
 *
 * <p>回傳值：只有「願望的凍結狀態因這次呼叫而改變」（從未凍結變成凍結、或全部解除）時，
 * 才回傳受影響的、還沒寄出的認領，呼叫端據此發通知；其餘情況回傳空。
 */
@Service
public class WishFreezeService {

    private final WishFreezeRepository freezes;
    private final ClaimRepository claims;

    public WishFreezeService(WishFreezeRepository freezes, ClaimRepository claims) {
        this.freezes = freezes;
        this.claims = claims;
    }

    /**
     * 加上一個凍結原因。管理員的原因（{@code ADMIN_*}）同時只能有一個，重複回 409；
     * 停權連動的原因是冪等的（已經有就略過）。
     */
    @Transactional
    public Optional<Claim> freeze(Wish wish, WishFreezeCause cause, String reason, UUID by) {
        wish.requireFreezable();
        List<WishFreeze> active = freezes.findByWishIdAndLiftedAtIsNullOrderByCreatedAtAsc(wish.getId());
        if (cause.isSystem()) {
            if (active.stream().anyMatch(f -> f.getCause() == cause)) {
                return Optional.empty();
            }
        } else if (active.stream().anyMatch(f -> !f.getCause().isSystem())) {
            throw new BusinessRuleException("WISH_ALREADY_FROZEN", "這個願望已經凍結了");
        }

        boolean wasFrozen = wish.isFrozen();
        freezes.save(new WishFreeze(wish.getId(), cause, reason, by));
        refreshState(wish);

        if (wasFrozen) {
            return Optional.empty();
        }
        return unshippedClaimOf(wish.getId()).map(claim -> {
            claim.pauseDeadline(Instant.now());
            return claim;
        });
    }

    /**
     * 管理員在願望上按「解除凍結」：解除所有管理員的原因。停權連動的原因不歸這裡管
     * （要由機構／認領者的復權解除），只剩那些時回 409。
     */
    @Transactional
    public Optional<Claim> liftAdminCauses(Wish wish, UUID by) {
        List<WishFreeze> active = freezes.findByWishIdAndLiftedAtIsNullOrderByCreatedAtAsc(wish.getId());
        List<WishFreeze> adminOnes = active.stream().filter(f -> !f.getCause().isSystem()).toList();
        if (adminOnes.isEmpty()) {
            throw new BusinessRuleException("WISH_NOT_FROZEN", active.isEmpty()
                    ? "這個願望目前沒有被凍結"
                    : "這個願望是因機構或認領者的停權而凍結，請從該停權處理，不能在願望上直接解除");
        }
        adminOnes.forEach(f -> f.lift(by));
        return afterLift(wish);
    }

    /** 解除某個停權連動的原因（機構復權、認領者復權時呼叫）。沒有這個原因就略過。 */
    @Transactional
    public Optional<Claim> liftCause(Wish wish, WishFreezeCause cause, UUID by) {
        List<WishFreeze> matching = freezes.findByWishIdAndLiftedAtIsNullOrderByCreatedAtAsc(wish.getId()).stream()
                .filter(f -> f.getCause() == cause).toList();
        if (matching.isEmpty()) {
            return Optional.empty();
        }
        matching.forEach(f -> f.lift(by));
        return afterLift(wish);
    }

    /** 機構「修改並重新送審」取代凍結：一次解除所有進行中的凍結紀錄（只發生在還沒被認領的願望）。 */
    @Transactional
    public void liftAll(UUID wishId, UUID by) {
        freezes.findByWishIdAndLiftedAtIsNullOrderByCreatedAtAsc(wishId).forEach(f -> f.lift(by));
    }

    @Transactional(readOnly = true)
    public List<WishFreeze> activeFreezes(UUID wishId) {
        return freezes.findByWishIdAndLiftedAtIsNullOrderByCreatedAtAsc(wishId);
    }

    private Optional<Claim> afterLift(Wish wish) {
        freezes.flush();
        refreshState(wish);
        if (wish.isFrozen()) {
            return Optional.empty();
        }
        return unshippedClaimOf(wish.getId()).map(claim -> {
            claim.resumeDeadline(Instant.now());
            return claim;
        });
    }

    /** 依進行中的紀錄重算願望上的反正規化欄位：最早的凍結時間、優先度最高的原因與理由。 */
    private void refreshState(Wish wish) {
        freezes.flush();
        List<WishFreeze> active = freezes.findByWishIdAndLiftedAtIsNullOrderByCreatedAtAsc(wish.getId());
        if (active.isEmpty()) {
            wish.applyFreezeState(null, null, null, null);
            return;
        }
        WishFreeze top = active.stream()
                .min(Comparator.comparing(f -> f.getCause().ordinal()))
                .orElseThrow();
        wish.applyFreezeState(active.get(0).getCreatedAt(), top.getReason(), top.getCreatedBy(), top.getCause());
    }

    private Optional<Claim> unshippedClaimOf(UUID wishId) {
        return claims.findFirstByWishIdAndStatusIn(wishId, EnumSet.of(ClaimStatus.CLAIMED));
    }
}
