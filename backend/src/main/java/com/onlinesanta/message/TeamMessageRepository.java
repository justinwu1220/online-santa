package com.onlinesanta.message;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamMessageRepository extends JpaRepository<TeamMessage, Long> {

    List<TeamMessage> findByOrganizationIdOrderByCreatedAtAscIdAsc(UUID organizationId);

    /** 機構還沒讀的團隊訊息。 */
    List<TeamMessage> findByOrganizationIdAndFromTeamTrueAndReadAtIsNull(UUID organizationId);

    long countByOrganizationIdAndFromTeamTrueAndReadAtIsNull(UUID organizationId);

    /** 團隊還沒讀的機構訊息（管理員後台）。 */
    List<TeamMessage> findByOrganizationIdAndFromTeamFalseAndReadAtIsNull(UUID organizationId);

    long countByFromTeamFalseAndReadAtIsNull();

    /** 每個機構最新的一則訊息，一次查完。 */
    @Query("""
            select m from TeamMessage m
             where m.id in (select max(x.id) from TeamMessage x group by x.organizationId)
            """)
    List<TeamMessage> findLatestPerOrganization();

    /** 每個機構團隊還沒讀的訊息數：[organizationId, count]。 */
    @Query("""
            select m.organizationId, count(m) from TeamMessage m
             where m.fromTeam = false and m.readAt is null
             group by m.organizationId
            """)
    List<Object[]> countUnreadByTeamPerOrganization();
}
