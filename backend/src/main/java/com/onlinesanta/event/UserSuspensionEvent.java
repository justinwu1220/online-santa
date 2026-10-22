package com.onlinesanta.event;

import java.util.UUID;

/** 認領者被停權或解除停權。通知該認領者本人；{@code reason} 是停權原因（解除時為 null）。 */
public record UserSuspensionEvent(UUID userId, boolean suspended, String reason) {
}
