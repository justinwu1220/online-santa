package com.onlinesanta.event;

import java.util.UUID;

/**
 * 逾期未寄送的認領被排程依「自動收回」政策收回了（通知 4.2）。通知原認領者。
 *
 * <p>不沿用 {@link ClaimReleasedByAdminEvent}：那封信說的是「管理員釋出」，這裡是系統依機構設定收回。
 * 只帶認領 id：排程整批在同一個交易裡執行，提交後 listener 再重新讀取認領與認領者。
 */
public record ClaimAutoReleasedEvent(UUID claimId) {
}
