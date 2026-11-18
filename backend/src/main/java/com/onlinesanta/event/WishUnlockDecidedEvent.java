package com.onlinesanta.event;

import java.util.UUID;

/**
 * 管理員核准或退回了機構的願望解除凍結申請。通知該願望所屬機構的 contactEmail。
 *
 * @param note 核准時是選填的留言，退回時是退回原因。可為 null。
 */
public record WishUnlockDecidedEvent(UUID wishId, boolean approved, String note) {
}
