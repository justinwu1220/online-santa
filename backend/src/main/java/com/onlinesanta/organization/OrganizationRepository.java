package com.onlinesanta.organization;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsByNameIgnoreCase(String name);

    Page<Organization> findByStatusOrderByCreatedAtAsc(OrganizationStatus status, Pageable pageable);
}
