package com.onlinesanta.organization;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsByNameIgnoreCase(String name);

    Page<Organization> findByStatusOrderByCreatedAtAsc(OrganizationStatus status, Pageable pageable);

    /**
     * 依狀態分組計數，供監控中心的統計使用。
     *
     * <p>用聚合查詢而非撈出全部再數——資料量成長後前者是常數成本，後者不是。
     */
    @Query("select o.status, count(o) from Organization o group by o.status")
    List<Object[]> countByStatus();
}
