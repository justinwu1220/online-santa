package com.onlinesanta.receipt;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimReceiptRepository extends JpaRepository<ClaimReceipt, UUID> {

    Optional<ClaimReceipt> findByClaimId(UUID claimId);
}
