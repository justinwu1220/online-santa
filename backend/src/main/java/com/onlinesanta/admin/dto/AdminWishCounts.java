package com.onlinesanta.admin.dto;

/** 管理員「願望」篩選列各項的數量（草稿不算）。 */
public record AdminWishCounts(
        long all,
        long review,
        long open,
        long claimed,
        long late,
        long done,
        long frozen,
        /** 機構送出、等管理員審核的解除凍結申請數 */
        long unlockRequests) {
}
