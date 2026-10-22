package com.onlinesanta.organization;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRevisionRepository extends JpaRepository<OrganizationRevision, Long> {

    List<OrganizationRevision> findByOrganizationIdOrderByCreatedAtAscIdAsc(UUID organizationId);
}
