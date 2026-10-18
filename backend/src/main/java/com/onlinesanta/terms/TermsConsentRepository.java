package com.onlinesanta.terms;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsConsentRepository extends JpaRepository<TermsConsent, UUID> {

    /** 該使用者對這份須知最近一次的同意（不論版本）。 */
    Optional<TermsConsent> findFirstByUserIdAndTermsTypeOrderByCreatedAtDesc(
            UUID userId, TermsType termsType);

    long countByUserIdAndTermsType(UUID userId, TermsType termsType);
}
