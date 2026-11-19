package com.onlinesanta.closing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosingMailLogRepository extends JpaRepository<ClosingMailLog, UUID> {

    List<ClosingMailLog> findByCampaignIdAndKind(UUID campaignId, String kind);
}
