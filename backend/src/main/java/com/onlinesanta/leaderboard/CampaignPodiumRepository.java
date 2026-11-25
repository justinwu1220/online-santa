package com.onlinesanta.leaderboard;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignPodiumRepository extends JpaRepository<CampaignPodium, UUID> {

    List<CampaignPodium> findByCampaignId(UUID campaignId);

    List<CampaignPodium> findByCampaignIdIn(List<UUID> campaignIds);
}
