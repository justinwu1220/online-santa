package com.onlinesanta.wish;

/** 願望歷程的事件類型。對應 wish_events.event_type 的 CHECK 約束。 */
public enum WishEventType {
    /** 第一次提交審核。 */
    SUBMITTED("提交審核"),
    /** 曾經上架過的願望，修改後再次提交審核。 */
    RESUBMITTED("修改後重新送審"),
    APPROVED("管理員核准"),
    REJECTED("管理員退回"),
    /** 機構撤回審核。 */
    WITHDRAWN("機構撤回審核"),
    /** 機構要修改已上架的願望：先從願望牆撤下。 */
    TAKEN_DOWN_FOR_EDIT("機構為修改而下架"),
    /** 機構沒有修改，直接恢復上架。 */
    RESTORED("機構恢復上架"),
    FROZEN("管理員凍結"),
    UNFROZEN("管理員解除凍結"),
    /** 凍結中的願望被機構修改並重新送審，凍結因此解除。 */
    FREEZE_LIFTED_BY_RESUBMIT("因機構修改重新送審而解除凍結"),
    /** 已停用：認領後內容鎖定，不再有「為修改內容收回認領」。只為顯示舊紀錄與滿足 V33 的 CHECK 約束而保留。 */
    RECALLED_FOR_EDIT("機構為修改願望內容收回認領");

    private final String label;

    WishEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
