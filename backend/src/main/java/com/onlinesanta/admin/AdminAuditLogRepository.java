package com.onlinesanta.admin;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AdminAuditLog> findByActionOrderByCreatedAtDesc(AdminAuditAction action, Pageable pageable);

    /** 「這筆資料被誰看過」——處理申訴時會需要。 */
    Page<AdminAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            AdminAuditTargetType targetType, UUID targetId, Pageable pageable);
}
