package com.onlinesanta.admin;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.CurrentUserService;

/**
 * 管理員操作的稽核紀錄。
 *
 * <p>寫入與呼叫端同一個交易：如果那個動作最後失敗回滾了，稽核紀錄也不該留下
 * ——記錄的是「實際發生過的事」，不是「試圖做的事」。
 */
@Service
public class AdminAuditService {

    private final AdminAuditLogRepository logs;
    private final CurrentUserService currentUser;

    public AdminAuditService(AdminAuditLogRepository logs, CurrentUserService currentUser) {
        this.logs = logs;
        this.currentUser = currentUser;
    }

    @Transactional
    public void record(AdminAuditAction action, UUID targetId, String detail) {
        logs.save(AdminAuditLog.of(
                currentUser.requireAdmin().userId(), action, targetId, detail));
    }

    @Transactional
    public void record(AdminAuditAction action, UUID targetId) {
        record(action, targetId, null);
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLog> list(AdminAuditAction action, Pageable pageable) {
        return action == null
                ? logs.findAllByOrderByCreatedAtDesc(pageable)
                : logs.findByActionOrderByCreatedAtDesc(action, pageable);
    }
}
