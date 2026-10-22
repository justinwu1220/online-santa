package com.onlinesanta.organization;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 停權期間機構資料的一次修改（欄位的舊→新，JSON 文字）。只增不改。見 V37。 */
@Entity
@Table(name = "organization_revisions")
public class OrganizationRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String changes;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected OrganizationRevision() {
        // JPA
    }

    public OrganizationRevision(UUID organizationId, String changes, UUID createdBy) {
        this.organizationId = organizationId;
        this.changes = changes;
        this.createdBy = createdBy;
    }

    public String getChanges() {
        return changes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
