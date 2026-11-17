package com.onlinesanta.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    /** 該使用者最近一張尚未使用的 token（驗證碼只看最新的一張）。 */
    Optional<AuthToken> findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            UUID userId, AuthTokenPurpose purpose);

    Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, AuthTokenPurpose purpose);

    List<AuthToken> findByUserIdAndPurposeAndUsedAtIsNull(UUID userId, AuthTokenPurpose purpose);

    Optional<AuthToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, AuthTokenPurpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(UUID userId, AuthTokenPurpose purpose, Instant after);

    /** 過期、或已使用超過一段時間的 token，供排程清理。 */
    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :cutoff or (t.usedAt is not null and t.usedAt < :cutoff)")
    int deleteStale(@Param("cutoff") Instant cutoff);
}
