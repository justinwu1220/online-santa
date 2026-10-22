package com.onlinesanta.suspension;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SuspensionRecordRepository extends JpaRepository<SuspensionRecord, Long> {

    /** 目前進行中（還沒解除）的停權紀錄。 */
    Optional<SuspensionRecord> findFirstByTargetTypeAndTargetIdAndLiftedAtIsNullOrderByCreatedAtDescIdDesc(
            SuspensionTargetType type, UUID targetId);
}
