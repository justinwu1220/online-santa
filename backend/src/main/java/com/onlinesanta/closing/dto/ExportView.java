package com.onlinesanta.closing.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.closing.ClosingExport;
import com.onlinesanta.closing.ExportKind;
import com.onlinesanta.closing.ExportStatus;

/** 匯出作業的進度。前端建立後反覆呼叫 advance，直到 READY（或 FAILED）。 */
public record ExportView(
        UUID id,
        UUID campaignId,
        ExportKind kind,
        com.onlinesanta.closing.doc.ClosingStage stage,
        ExportStatus status,
        int total,
        int prepared,
        int skipped,
        Long sizeBytes,
        String error,
        Instant expiresAt,
        Instant createdAt) {

    public static ExportView from(ClosingExport e) {
        return new ExportView(e.getId(), e.getCampaignId(), e.getKind(), e.getStage(), e.getStatus(), e.getTotal(),
                e.getPrepared(), e.getSkipped(), e.getSizeBytes(), e.getError(), e.getExpiresAt(), e.getCreatedAt());
    }
}
