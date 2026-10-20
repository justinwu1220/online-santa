package com.onlinesanta.event;

import java.util.UUID;

/** 機構收回認領（還沒寄出）。通知該筆認領的捐贈者。 */
public record ClaimRecalledEvent(UUID claimId, String reason) {
}
