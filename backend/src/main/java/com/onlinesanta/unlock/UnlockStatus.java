package com.onlinesanta.unlock;

/** 解除申請的狀態。對應 unlock_requests.status 的 CHECK 約束。 */
public enum UnlockStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
