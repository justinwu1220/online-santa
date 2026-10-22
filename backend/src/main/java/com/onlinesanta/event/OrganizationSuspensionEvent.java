package com.onlinesanta.event;

import java.util.UUID;

/** 機構被停權或解除停權。通知機構的聯絡信箱；{@code reason} 是停權原因（解除時為 null）。 */
public record OrganizationSuspensionEvent(UUID organizationId, boolean suspended, String reason) {
}
