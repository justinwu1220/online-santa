package com.onlinesanta.wish.dto;

import java.util.List;

import com.onlinesanta.claim.dto.ClaimOrgView;

/**
 * 機構後台的願望抽屜：願望本身、目前的認領（完整資料，沒有的話為 null）、過往被收回或取消的認領紀錄。
 */
public record WishOrgDetailView(
        WishOrgView wish,
        ClaimOrgView activeClaim,
        List<PastClaimView> pastClaims,
        /** 最近一筆解除凍結申請（待審或已決定）；沒有申請過為 null */
        com.onlinesanta.unlock.UnlockRequestView unlockRequest,
        /** 可以申請解除凍結：管理員凍結中、沒有待審的申請 */
        boolean canRequestUnlock,
        /** 可以隨申請附上修改後的內容：凍結原因是內容疑慮 */
        boolean canReviseContent) {
}
