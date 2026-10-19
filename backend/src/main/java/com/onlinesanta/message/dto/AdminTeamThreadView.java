package com.onlinesanta.message.dto;

import java.time.Instant;
import java.util.UUID;

/** 管理員「團隊對話」左側清單的一條對話：機構、最新一則、團隊還沒讀的數量。 */
public record AdminTeamThreadView(
        UUID organizationId,
        String organizationName,
        String lastBody,
        boolean lastFromTeam,
        Instant lastAt,
        long unread) {
}
