package com.onlinesanta.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 批次更新繞過 Hibernate 的一級快取：同一個交易裡先讀過的實體不會看到這次更新，
     * 之後再讀仍然是「沒撤銷」。所以先 flush（避免丟掉還沒寫出去的修改）、更新後清快取。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :at where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :at where t.userId = :userId and t.revokedAt is null")
    int revokeAllOf(@Param("userId") UUID userId, @Param("at") Instant at);

    /** 過期、或撤銷超過一段時間的 token，供排程清理。 */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff or (t.revokedAt is not null and t.revokedAt < :cutoff)")
    int deleteStale(@Param("cutoff") Instant cutoff);
}
