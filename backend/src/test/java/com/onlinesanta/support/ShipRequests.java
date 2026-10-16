package com.onlinesanta.support;

import java.time.LocalDate;

import com.onlinesanta.claim.dto.ReceiptRequest;
import com.onlinesanta.claim.dto.ShipRequest;

/** 測試用的回報寄送請求。禮物資訊用固定的合理預設值，測試只需要關心物流資訊。 */
public final class ShipRequests {

    private ShipRequests() {
    }

    public static ShipRequest of(String carrier, String trackingNumber) {
        return new ShipRequest(carrier, trackingNumber, "繪本與文具組", 1, 500, LocalDate.now(), null);
    }

    /** 申請捐贈收據的回報寄送，收據欄位用固定的合理預設值。 */
    public static ShipRequest withReceipt(String carrier, String trackingNumber) {
        return withReceipt(carrier, trackingNumber,
                new ReceiptRequest("王大明", "A123456789", "receipt@example.com", "台北市中正區某路 1 號"));
    }

    public static ShipRequest withReceipt(String carrier, String trackingNumber, ReceiptRequest receipt) {
        return new ShipRequest(carrier, trackingNumber, "繪本與文具組", 1, 500, LocalDate.now(), receipt);
    }
}
