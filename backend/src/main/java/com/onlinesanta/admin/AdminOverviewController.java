package com.onlinesanta.admin;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.admin.dto.AuditLogView;
import com.onlinesanta.admin.dto.PlatformStatsView;
import com.onlinesanta.common.PageResponse;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 監控中心的儀表板與稽核軌跡。 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "監控中心", description = "跨機構的唯讀檢視。存取個人資料會寫入稽核紀錄")
public class AdminOverviewController {

    private final AdminStatsService stats;
    private final AdminAuditService audit;
    private final UserRepository users;

    public AdminOverviewController(AdminStatsService stats,
                                   AdminAuditService audit,
                                   UserRepository users) {
        this.stats = stats;
        this.audit = audit;
        this.users = users;
    }

    @GetMapping("/stats")
    @Operation(summary = "全站統計", description = "機構、願望、認領、使用者的狀態分佈")
    public PlatformStatsView stats() {
        return stats.collect();
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "管理員操作的稽核軌跡",
            description = "對所有管理員公開——有權限的人不該能安靜地做任何事")
    public PageResponse<AuditLogView> auditLogs(
            @RequestParam(required = false) AdminAuditAction action,
            @PageableDefault(size = 30) Pageable pageable) {
        Page<AdminAuditLog> page = audit.list(action, pageable);

        // 一次把這一頁涉及的管理員撈齊，不要逐筆查
        Map<UUID, String> emails = users
                .findByIdIn(page.getContent().stream().map(AdminAuditLog::getAdminUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getEmail, (first, second) -> first));

        return PageResponse.of(page,
                log -> AuditLogView.from(log, emails.get(log.getAdminUserId())));
    }
}
