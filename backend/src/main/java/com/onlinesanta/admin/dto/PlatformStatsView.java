package com.onlinesanta.admin.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 監控中心的全站統計。
 *
 * <p>刻意用「狀態 → 筆數」的 map 而非把每個狀態列成欄位：新增一個狀態時後端與前端
 * 都不必改結構，前端照著 map 逐項渲染即可。
 *
 * @param overdueClaims 逾期未寄送的認領。這是最需要盯的數字——它代表有孩子的願望
 *                      正被卡著，而捐贈者可能已經放棄了
 */
public record PlatformStatsView(
        Map<String, Long> organizations,
        Map<String, Long> wishes,
        Map<String, Long> claims,
        Map<String, Long> users,
        long overdueClaims,
        long pendingOrganizations,
        long availableWishes,
        Instant generatedAt) {
}
