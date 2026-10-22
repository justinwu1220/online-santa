package com.onlinesanta.claim;

import com.onlinesanta.organization.OrganizationStatus;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishFreezeCause;

/**
 * 一筆認領目前被什麼限制。<strong>全站只有這裡判定</strong>：寄送、取消、收回、確認收到、傳訊、附件上傳，
 * 以及認領者與機構看到的提示文字，都用它，不要各自檢查凍結或停權。
 *
 * <p>{@code frozen_cause} 反正規化在願望上，機構狀態隨願望一起載入，所以判定不需要額外查詢。
 */
public enum ClaimRestriction {
    NONE,
    /** 管理員凍結了這個願望（內容疑慮或其他原因）。 */
    WISH_FROZEN,
    /** 機構被停權。 */
    ORG_SUSPENDED,
    /** 認領者被停權。 */
    DONOR_SUSPENDED;

    public static ClaimRestriction of(Claim claim) {
        return of(claim.getWish());
    }

    public static ClaimRestriction of(Wish wish) {
        WishFreezeCause cause = wish.getFrozenCause();
        if (cause == WishFreezeCause.DONOR_SUSPENDED) {
            return DONOR_SUSPENDED;
        }
        // 機構停權即使還沒連動出凍結紀錄（例如舊資料），也視為受限
        if (cause == WishFreezeCause.ORG_SUSPENDED
                || wish.getOrganization().getStatus() == OrganizationStatus.SUSPENDED) {
            return ORG_SUSPENDED;
        }
        return cause != null ? WISH_FROZEN : NONE;
    }

    public boolean isRestricted() {
        return this != NONE;
    }

    /** 聯絡信箱，各種限制提示共用。 */
    public static final String CONTACT_EMAIL = "onlinesanta2026@gmail.com";

    /** 認領者看到的說明（錯誤訊息與認領頁提示共用，前端也有一份相同的文字）。 */
    public String donorMessage() {
        return switch (this) {
            case NONE -> "";
            case WISH_FROZEN -> "平台正在確認這個願望的內容，暫停處理期間不用寄出；解除後會通知你";
            case ORG_SUSPENDED -> "此機構已被管理員暫時停權，正在積極處理中。若有問題可聯絡：" + CONTACT_EMAIL;
            case DONOR_SUSPENDED -> "你的帳號目前受到限制，這筆認領暫停處理。若有問題可聯絡：" + CONTACT_EMAIL;
        };
    }

    /** 機構看到的說明。 */
    public String organizationMessage() {
        return switch (this) {
            case NONE -> "";
            case WISH_FROZEN -> "管理員正在處理這個願望，期間無法收回認領、確認收到或傳訊；有問題請到「與團隊對話」";
            case ORG_SUSPENDED -> "貴機構目前被停權，無法對願望進行操作；請到「與團隊對話」聯繫管理員";
            case DONOR_SUSPENDED -> "認領者出現違規行為，正在積極處理中。";
        };
    }
}
