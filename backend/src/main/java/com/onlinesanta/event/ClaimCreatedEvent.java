package com.onlinesanta.event;

import java.util.UUID;

/** 認領成立。通知機構的 contactEmail。 */
public record ClaimCreatedEvent(UUID claimId) {
}
