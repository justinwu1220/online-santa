package com.onlinesanta.event;

import java.util.UUID;

/**
 * 新的站內訊息，且發送當下判定不需要節流。
 *
 * <p>只有 {@code MessageService.send} 判斷「對方沒有還沒讀的舊訊息」時才會發布這個
 * 事件——防轟炸的節流判斷在發布事件之前就做完了，這裡收到事件就是要寄信，
 * 不重複判斷一次。
 */
public record NewMessageEvent(UUID claimId, UUID senderUserId) {
}
