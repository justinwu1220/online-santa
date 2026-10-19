package com.onlinesanta.wish.dto;

/**
 * 機構「願望管理」篩選列的各項數量。{@code claimed} 是 waiting＋overdue＋shipped 的合計
 * （設計稿的「已認領」），細項另外給，供頂端的待辦提示用。
 */
public record OrganizationStageCounts(
        long all,
        long draft,
        long pendingReview,
        long open,
        long claimed,
        long done,
        long claimedWaiting,
        long claimedOverdue,
        long shipped) {
}
