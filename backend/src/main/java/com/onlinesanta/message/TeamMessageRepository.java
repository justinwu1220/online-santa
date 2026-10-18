package com.onlinesanta.message;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMessageRepository extends JpaRepository<TeamMessage, Long> {

    List<TeamMessage> findByOrganizationIdOrderByCreatedAtAscIdAsc(UUID organizationId);

    /** 機構還沒讀的團隊訊息。 */
    List<TeamMessage> findByOrganizationIdAndFromTeamTrueAndReadAtIsNull(UUID organizationId);

    long countByOrganizationIdAndFromTeamTrueAndReadAtIsNull(UUID organizationId);
}
