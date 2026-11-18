package com.onlinesanta.closing;

import java.util.UUID;

/** 開立了一張收據（交易提交後才產生 PDF）。 */
public record DonationReceiptIssuedEvent(UUID receiptId) {
}
