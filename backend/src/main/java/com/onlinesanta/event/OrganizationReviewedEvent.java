package com.onlinesanta.event;

import java.util.UUID;

/** 機構審核決定（核准或駁回）。通知機構的 contactEmail。 */
public record OrganizationReviewedEvent(UUID organizationId, boolean approved) {
}
