package com.onlinesanta.event;

import java.util.UUID;

/**
 * 機構停權（或復權）連動了一筆還沒寄出的認領的凍結。通知該筆認領的認領者：
 * {@code suspended} 為 true 是停權（暫時不用寄出），false 是復權（期限順延、可以繼續）。
 */
public record OrgSuspensionClaimEvent(UUID claimId, boolean suspended) {
}
