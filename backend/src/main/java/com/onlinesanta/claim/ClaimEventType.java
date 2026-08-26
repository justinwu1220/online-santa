package com.onlinesanta.claim;

/** 認領歷程的事件類型。對應 claim_events.event_type 的 CHECK 約束。 */
public enum ClaimEventType {
    CLAIMED,
    SHIPPED,
    RECEIVED,
    COMPLETED,
    /** 機構在後台手動收回。 */
    RELEASED_MANUAL,
    /** 逾期未寄送，由排程自動收回（M5）。 */
    RELEASED_AUTO,
    CANCELLED,
    /** 機構上傳了送禮回饋照片（M4）。 */
    FEEDBACK_UPLOADED
}
