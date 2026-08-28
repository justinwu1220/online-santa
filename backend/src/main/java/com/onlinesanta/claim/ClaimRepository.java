package com.onlinesanta.claim;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    /** 每位民眾同時進行中的認領數上限檢查。 */
    long countByDonorIdAndStatusIn(UUID donorId, Collection<ClaimStatus> statuses);

    @EntityGraph(attributePaths = {"wish", "wish.organization", "donor"})
    Optional<Claim> findWithDetailsById(UUID id);

    /** 監控中心的跨機構檢視。 */
    @EntityGraph(attributePaths = {"wish", "wish.organization", "donor"})
    Page<Claim> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"wish", "wish.organization", "donor"})
    Page<Claim> findByStatus(ClaimStatus status, Pageable pageable);

    /** 監控中心的逾期清單（跨機構）。 */
    @EntityGraph(attributePaths = {"wish", "wish.organization", "donor"})
    @Query("""
            select c from Claim c
            where c.status = com.onlinesanta.claim.ClaimStatus.CLAIMED
              and c.shipDeadlineAt is not null
              and c.shipDeadlineAt < :now
            """)
    Page<Claim> findAllOverdue(@Param("now") Instant now, Pageable pageable);

    /** 依狀態分組計數，供監控中心的統計使用。 */
    @Query("select c.status, count(c) from Claim c group by c.status")
    List<Object[]> countByStatus();

    /** 逾期未寄送的筆數。條件與 findOverdue 一致，但不把資料撈出來。 */
    @Query("""
            select count(c) from Claim c
            where c.status = com.onlinesanta.claim.ClaimStatus.CLAIMED
              and c.shipDeadlineAt is not null
              and c.shipDeadlineAt < :now
            """)
    long countOverdue(@Param("now") Instant now);

    @EntityGraph(attributePaths = {"wish", "wish.organization"})
    Page<Claim> findByDonorIdOrderByClaimedAtDesc(UUID donorId, Pageable pageable);

    @EntityGraph(attributePaths = {"wish", "donor"})
    @Query("select c from Claim c where c.wish.organization.id = :organizationId")
    Page<Claim> findByOrganizationId(@Param("organizationId") UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"wish", "donor"})
    @Query("select c from Claim c where c.wish.organization.id = :organizationId and c.status = :status")
    Page<Claim> findByOrganizationIdAndStatus(@Param("organizationId") UUID organizationId,
                                              @Param("status") ClaimStatus status,
                                              Pageable pageable);

    /**
     * 機構後台的逾期提醒：手動釋回政策的機構靠這個清單自行決定要不要收回。
     */
    @EntityGraph(attributePaths = {"wish", "donor"})
    @Query("""
            select c from Claim c
            where c.wish.organization.id = :organizationId
              and c.status = com.onlinesanta.claim.ClaimStatus.CLAIMED
              and c.shipDeadlineAt is not null
              and c.shipDeadlineAt < :now
            """)
    Page<Claim> findOverdueByOrganizationId(@Param("organizationId") UUID organizationId,
                                            @Param("now") Instant now,
                                            Pageable pageable);

    /**
     * 逾期未寄送的認領，供釋回排程使用。
     *
     * <p>只掃 CLAIMED：已寄出的不算逾期。有 idx_claims_overdue 這個部分索引支撐。
     */
    @Query("""
            select c from Claim c
            join fetch c.wish w
            join fetch w.organization
            where c.status = com.onlinesanta.claim.ClaimStatus.CLAIMED
              and c.shipDeadlineAt is not null
              and c.shipDeadlineAt < :now
            """)
    List<Claim> findOverdue(@Param("now") Instant now);
}
