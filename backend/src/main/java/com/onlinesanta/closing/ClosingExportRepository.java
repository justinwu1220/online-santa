package com.onlinesanta.closing;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosingExportRepository extends JpaRepository<ClosingExport, UUID> {
}
