package com.onlinesanta.claim;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

/**
 * 監控中心認領清單的篩選條件。
 *
 * <p>篩選維度（狀態、逾期、年度、捐贈收據）可以任意組合，如果每種組合各寫一支 repository
 * 方法，每多一個維度方法數就翻倍。改用 Specification 組合，每個維度只寫一次。
 *
 * <p>年度的區間是半開的 {@code [from, to)}，且一定有值——不用 {@code (:from is null or ...)}
 * 這種寫法，Instant 參數在 Postgres 會推不出型別（見 {@link ClaimRepository} 的說明）。
 * Specification 只在有篩選時才加上那個條件，沒有這個問題。
 */
public final class ClaimSpecifications {

    private ClaimSpecifications() {
    }

    /**
     * @param status          null 代表不篩選
     * @param overdueOnly     只要逾期未寄送的（狀態為 CLAIMED 且寄送期限已過）。
     *                        與 status 同時給時以逾期為準，結果等於 status = CLAIMED 的逾期子集
     * @param claimedFrom     認領時間區間的起點（含），與 {@code claimedTo} 一起給或一起不給
     * @param claimedTo       認領時間區間的終點（不含）
     * @param receiptRequested true 只要申請了捐贈收據的；false 不篩選
     */
    public static Specification<Claim> forAdminList(ClaimStatus status, boolean overdueOnly, Instant now,
                                                    Instant claimedFrom, Instant claimedTo,
                                                    boolean receiptRequested) {
        List<Specification<Claim>> parts = new ArrayList<>();

        if (overdueOnly) {
            // 與 ClaimRepository.findAllOverdue 同一個條件
            parts.add((root, query, cb) -> cb.and(
                    cb.equal(root.get("status"), ClaimStatus.CLAIMED),
                    cb.isNotNull(root.get("shipDeadlineAt")),
                    cb.lessThan(root.get("shipDeadlineAt"), now)));
        } else if (status != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (claimedFrom != null && claimedTo != null) {
            parts.add((root, query, cb) -> cb.and(
                    cb.greaterThanOrEqualTo(root.get("claimedAt"), claimedFrom),
                    cb.lessThan(root.get("claimedAt"), claimedTo)));
        }

        if (receiptRequested) {
            parts.add((root, query, cb) -> cb.isTrue(root.get("receiptRequested")));
        }

        return Specification.allOf(parts);
    }
}
