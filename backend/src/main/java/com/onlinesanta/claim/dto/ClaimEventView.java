package com.onlinesanta.claim.dto;

import java.time.Instant;

import com.onlinesanta.claim.ClaimEvent;
import com.onlinesanta.claim.ClaimEventType;

/** 認領歷程的單一事件。 */
public record ClaimEventView(ClaimEventType eventType, String note, Instant occurredAt) {

    public static ClaimEventView from(ClaimEvent event) {
        return new ClaimEventView(event.getEventType(), event.getNote(), event.getCreatedAt());
    }
}
