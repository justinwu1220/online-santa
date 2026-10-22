package com.onlinesanta.event;

import java.util.UUID;

/** 管理員釋出了一筆認領（例如認領者違規）。通知該筆認領的認領者。 */
public record ClaimReleasedByAdminEvent(UUID claimId, String reason) {
}
