package com.onlinesanta.message.dto;

import java.util.List;

/**
 * 機構看一個願望的對話。
 *
 * @param claimantName 目前認領者的顯示名稱（對話分頁標題「與 {認領者} 的對話」用）；
 *                     還沒有人認領時為 null，這時只有系統通知，不能傳訊息
 * @param unread       這個願望的未讀數
 */
public record WishConversationItem(String claimantName, boolean canSend, long unread,
                                   List<MessageView> messages) {
}
