package com.onlinesanta.admin;

/** 稽核紀錄指向的對象類型。對應 admin_audit_logs.target_type 的 CHECK 約束。 */
public enum AdminAuditTargetType {
    CLAIM,
    ORGANIZATION,
    /** 不針對特定資源的系統動作，例如觸發排程。 */
    SYSTEM
}
