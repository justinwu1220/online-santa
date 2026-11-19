package com.onlinesanta.closing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignFinanceRepository extends JpaRepository<CampaignFinance, UUID> {

    List<CampaignFinance> findAllByCampaignIdOrderByPeriodYearAscPeriodMonthAsc(UUID campaignId);
}
