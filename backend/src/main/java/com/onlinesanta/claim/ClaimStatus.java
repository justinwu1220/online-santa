package com.onlinesanta.claim;

import java.util.EnumSet;
import java.util.Set;

/**
 * 認領的流程狀態，同時定義合法的狀態轉換。
 *
 * <p>把轉換規則放在 enum 上，是為了讓「哪些流轉是合法的」只有一個答案。
 * 任何不在表上的轉換一律被 {@link com.onlinesanta.claim.ClaimService} 擋下並回 409，
 * 不必在每個端點各寫一次 if 判斷。
 */
public enum ClaimStatus {

    /** 已認領，等待捐贈者寄出。 */
    CLAIMED,
    /** 已寄出，等待機構確認收到。 */
    SHIPPED,
    /** 機構已收到禮物。 */
    RECEIVED,
    /** 機構已回傳送禮回饋，整段流程結束。 */
    COMPLETED,
    /** 機構收回認領（逾期未寄或聯繫不上），願望重新上架。 */
    RELEASED,
    /** 捐贈者主動取消，願望重新上架。 */
    CANCELLED;

    /** 已結束、不會再流轉的狀態。 */
    private static final Set<ClaimStatus> TERMINAL = EnumSet.of(COMPLETED, RELEASED, CANCELLED);

    /**
     * 仍佔用著願望的狀態——必須與資料庫 uq_active_claim_per_wish 的 WHERE 條件一致，
     * 否則應用層與資料庫層對「有效認領」的認定會分歧。
     */
    private static final Set<ClaimStatus> OCCUPYING =
            EnumSet.of(CLAIMED, SHIPPED, RECEIVED, COMPLETED);

    public Set<ClaimStatus> allowedNextStates() {
        return switch (this) {
            // 尚未寄出：可以寄出、被機構收回，或由捐贈者自行取消
            case CLAIMED -> EnumSet.of(SHIPPED, RELEASED, CANCELLED);
            // 已寄出就不能再收回或取消——否則會變成雙重送禮
            case SHIPPED -> EnumSet.of(RECEIVED);
            case RECEIVED -> EnumSet.of(COMPLETED);
            case COMPLETED, RELEASED, CANCELLED -> EnumSet.noneOf(ClaimStatus.class);
        };
    }

    public boolean canTransitionTo(ClaimStatus next) {
        return allowedNextStates().contains(next);
    }

    /** 是否仍佔用著願望，亦即該願望不可被他人認領。 */
    public boolean occupiesWish() {
        return OCCUPYING.contains(this);
    }

    /** 佔用中的狀態集合，供查詢「進行中的認領」使用。 */
    public static Set<ClaimStatus> occupyingStates() {
        return EnumSet.copyOf(OCCUPYING);
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
