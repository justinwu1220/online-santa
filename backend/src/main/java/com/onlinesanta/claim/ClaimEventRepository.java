package com.onlinesanta.claim;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimEventRepository extends JpaRepository<ClaimEvent, Long> {

    List<ClaimEvent> findByClaimIdOrderByCreatedAtAsc(UUID claimId);

    /**
     * 機構年度統計的「逾期自動釋回」次數。
     *
     * <p>cohort 制下歸屬的是<strong>認領當年</strong>而非事件發生的時間——用原生 SQL
     * join 回 claims／wishes：{@link ClaimEvent} 只存裸 UUID，不是 JPA 關聯，
     * JPQL 無法直接走物件圖跨表查詢。
     */
    @Query(value = """
            select count(*)
              from claim_events ce
              join claims c on c.id = ce.claim_id
              join wishes w on w.id = c.wish_id
             where w.organization_id = :organizationId
               and ce.event_type = 'RELEASED_AUTO'
               and c.claimed_at >= :from
               and c.claimed_at < :to
            """, nativeQuery = true)
    long countAutoReleasedForOrganization(@Param("organizationId") UUID organizationId,
                                          @Param("from") Instant from, @Param("to") Instant to);
}
