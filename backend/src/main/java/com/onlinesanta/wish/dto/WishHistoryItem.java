package com.onlinesanta.wish.dto;

import java.time.Instant;

import com.onlinesanta.wish.WishEvent;

/** 願望歷程的一筆（管理員抽屜的時間軸）。 */
public record WishHistoryItem(String type, String label, Instant at, String note) {

    public static WishHistoryItem from(WishEvent event) {
        return new WishHistoryItem(event.getEventType().name(), event.getEventType().getLabel(),
                event.getCreatedAt(), event.getNote());
    }
}
