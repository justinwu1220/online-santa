package com.onlinesanta.event;

import java.util.UUID;

/** 管理員凍結或解除凍結了一個已被認領的願望。通知該筆認領的捐贈者；{@code frozen} 為 true 是凍結。 */
public record WishFrozenEvent(UUID claimId, boolean frozen) {
}
