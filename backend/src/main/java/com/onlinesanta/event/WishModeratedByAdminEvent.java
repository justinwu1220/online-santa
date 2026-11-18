package com.onlinesanta.event;

import java.util.UUID;

/**
 * 管理員處置了一個願望（凍結、修改內容、移除信件照片、直接解除凍結）。通知願望所屬機構的 contactEmail。
 *
 * <p>事件只帶「做了什麼」與管理員留的原因；願望現在是否仍凍結、公開狀態等，由 listener 在交易提交後
 * 重新讀取——同一個動作可能因為其他凍結原因仍在而沒有真的解凍，信不能照動作名稱說話。
 *
 * @param reason 管理員填的原因（凍結、修改、移除照片必填；直接解除沒有）。可為 null。
 */
public record WishModeratedByAdminEvent(UUID wishId, Action action, String reason) {

    public enum Action {
        FROZEN,
        CONTENT_EDITED,
        PHOTO_REMOVED,
        UNFROZEN
    }
}
