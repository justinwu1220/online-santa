package com.onlinesanta.changelog;

/** 個資異動紀錄的對象。沿用 unlock_requests／suspension_records 的判別式寫法。 */
public enum ProfileChangeTargetType {
    /** 認領者（使用者帳號本身的資料）。 */
    USER,
    /** 機構。 */
    ORGANIZATION
}
