package com.onlinesanta.closing;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosingExportRepository extends JpaRepository<ClosingExport, UUID> {

    /** 暫存期限已過、壓縮檔該清除的匯出作業。 */
    java.util.List<ClosingExport> findByStatusAndExpiresAtBefore(ExportStatus status, java.time.Instant cutoff);
}
