package com.onlinesanta.event;

import java.util.UUID;

/** 管理員退回了機構的解除停權申請（機構仍停權中）。通知機構的 contactEmail。 */
public record OrganizationUnlockRejectedEvent(UUID organizationId, String note) {
}
