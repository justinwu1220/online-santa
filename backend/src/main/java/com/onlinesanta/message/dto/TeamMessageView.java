package com.onlinesanta.message.dto;

import java.time.Instant;

import com.onlinesanta.message.TeamMessage;

/** 與團隊的一則訊息。{@code fromTeam=true} 是團隊寄給機構的（氣泡靠左，署名「線上聖誕老人團隊」）。 */
public record TeamMessageView(Long id, String body, boolean fromTeam, boolean read, Instant sentAt) {

    public static TeamMessageView from(TeamMessage message) {
        return new TeamMessageView(message.getId(), message.getBody(), message.isFromTeam(),
                message.getReadAt() != null, message.getCreatedAt());
    }
}
