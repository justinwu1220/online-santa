package com.onlinesanta.unlock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnlockRequestRepository extends JpaRepository<UnlockRequest, Long> {

    Optional<UnlockRequest> findFirstByTargetTypeAndTargetIdAndStatus(
            UnlockTargetType type, UUID targetId, UnlockStatus status);

    /** 某對象最近的一筆申請（待審或已決定），機構看自己的申請進度用。 */
    Optional<UnlockRequest> findFirstByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(
            UnlockTargetType type, UUID targetId);

    /** 這些對象之中有待審申請的 id，管理員清單標示「有解除申請」用。 */
    @Query("select r.targetId from UnlockRequest r where r.targetType = :type and r.status = "
            + "com.onlinesanta.unlock.UnlockStatus.PENDING and r.targetId in :ids")
    List<UUID> findPendingTargetIds(@Param("type") UnlockTargetType type, @Param("ids") Collection<UUID> ids);

    long countByTargetTypeAndStatus(UnlockTargetType type, UnlockStatus status);
}
