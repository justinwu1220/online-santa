package com.onlinesanta.unlock;

/** 解除申請的對象。對應 unlock_requests.target_type 的 CHECK 約束。 */
public enum UnlockTargetType {
    /** 願望（解除凍結）。 */
    WISH,
    /** 機構（解除停權）。 */
    ORGANIZATION
}
