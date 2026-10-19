package com.onlinesanta.wish.dto;

import java.util.List;

import com.onlinesanta.claim.dto.ClaimOrgView;

/**
 * 機構後台的願望抽屜：願望本身、目前的認領（完整資料，沒有的話為 null）、過往被收回或取消的認領紀錄。
 */
public record WishOrgDetailView(
        WishOrgView wish,
        ClaimOrgView activeClaim,
        List<PastClaimView> pastClaims) {
}
