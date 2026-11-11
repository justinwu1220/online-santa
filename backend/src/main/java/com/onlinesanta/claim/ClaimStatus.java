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

    /**
     * 佔著<strong>捐贈者名額</strong>的狀態。
     *
     * <p>與 {@link #OCCUPYING} 只差一個 COMPLETED，但兩者回答的是不同的問題：
     *
     * <ul>
     *   <li>「這個<strong>願望</strong>被佔著嗎」——必須含 COMPLETED。禮物已經送到，
     *       願望不該回到願望牆讓別人再領一次，所以資料庫的
     *       {@code uq_active_claim_per_wish} 也含它。
     *   <li>「這個<strong>人</strong>手上還有幾筆進行中」——不該含 COMPLETED。他已經
     *       把禮物送到了，那正是名額該釋放的時刻。
     * </ul>
     *
     * <p>這兩個集合曾經共用，後果是累計完成三筆的捐贈者被<strong>永久鎖死</strong>——
     * COMPLETED 是終端狀態，計數再也不會下降。而放著不寄被收回（RELEASED）的人反而
     * 不受影響，等於系統懲罰了最可靠的捐贈者。所以即使只差一個值，也要分成兩個集合。
     */
    private static final Set<ClaimStatus> DONOR_QUOTA = EnumSet.of(CLAIMED, SHIPPED, RECEIVED);

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

    /**
     * 仍佔用著<strong>願望</strong>的狀態集合，供「這個願望還能不能被認領」使用。
     *
     * <p>不要拿來算某個人手上有幾筆進行中——那要用 {@link #donorQuotaStates()}。
     */
    public static Set<ClaimStatus> occupyingStates() {
        return EnumSet.copyOf(OCCUPYING);
    }

    /**
     * 佔著捐贈者名額的狀態集合，供「每人同時進行中的認領上限」使用。
     *
     * <p>刻意與 {@link #occupyingStates()} 分開，理由見 {@link #DONOR_QUOTA}。
     * 要改動時請先確認改的是哪一個問題——兩者看起來幾乎一樣，混用過一次了。
     */
    public static Set<ClaimStatus> donorQuotaStates() {
        return EnumSet.copyOf(DONOR_QUOTA);
    }

    /**
     * 這筆認領還能不能傳訊息。已完成的認領是成功收尾，雙方的關係還在，可以繼續聊
     * （謝卡、後續回報）；已收回、已取消的認領已經失效，願望會回到願望牆讓別人認領，不再開放。
     */
    public boolean allowsMessages() {
        return this != RELEASED && this != CANCELLED;
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
