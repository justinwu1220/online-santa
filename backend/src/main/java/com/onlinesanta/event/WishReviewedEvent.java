package com.onlinesanta.event;

import java.util.UUID;

/**
 * 管理員核准或退回了一個願望。
 *
 * @param reason 退回時的原因；核准為 null
 */
public record WishReviewedEvent(UUID wishId, boolean approved, String reason) {
}
