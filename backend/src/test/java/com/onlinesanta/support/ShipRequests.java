package com.onlinesanta.support;

import java.time.LocalDate;

import com.onlinesanta.claim.dto.ShipRequest;

/** 測試用的回報寄送請求。禮物資訊用固定的合理預設值，測試只需要關心物流資訊。 */
public final class ShipRequests {

    private ShipRequests() {
    }

    public static ShipRequest of(String carrier, String trackingNumber) {
        return new ShipRequest(carrier, trackingNumber, "繪本與文具組", 1, 500, LocalDate.now());
    }
}
