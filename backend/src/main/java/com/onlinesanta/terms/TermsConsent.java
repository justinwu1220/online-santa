package com.onlinesanta.terms;

import java.util.UUID;

import com.onlinesanta.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 一次同意。同意時間就是 {@link #getCreatedAt()}。只新增、不修改，保留每個版本的同意歷史。
 */
@Entity
@Table(name = "terms_consents")
public class TermsConsent extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, updatable = false, length = 20)
    private TermsType termsType;

    @Column(nullable = false, updatable = false, length = 50)
    private String version;

    protected TermsConsent() {
        // JPA
    }

    public TermsConsent(UUID userId, TermsType termsType, String version) {
        this.userId = userId;
        this.termsType = termsType;
        this.version = version;
    }

    public UUID getUserId() {
        return userId;
    }

    public TermsType getTermsType() {
        return termsType;
    }

    public String getVersion() {
        return version;
    }
}
