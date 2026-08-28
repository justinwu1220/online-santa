package com.onlinesanta.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.job.ClaimReleaseService;
import com.onlinesanta.job.dto.ReleaseSweepResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 讓管理員手動觸發排程工作。
 *
 * <p>與 Cloud Scheduler 呼叫的 {@code /internal/jobs/**} 是同一段邏輯，差別只在
 * 由誰驗證身分。存在的理由是實務需求：活動期間管理員可能需要立刻跑一次掃描，
 * 而不是等到隔天排程。順帶讓本機開發不必偽造 Google 的 OIDC token 也能測。
 */
@RestController
@RequestMapping("/api/admin/jobs")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理後台", description = "平台管理員的機構審核")
public class AdminJobController {

    private final ClaimReleaseService releases;
    private final AdminAuditService audit;

    public AdminJobController(ClaimReleaseService releases, AdminAuditService audit) {
        this.releases = releases;
        this.audit = audit;
    }

    @PostMapping("/release-expired-claims")
    @Operation(summary = "立即執行逾期認領掃描",
            description = "AUTO 政策的機構會自動釋回；MANUAL 政策只列入後台的逾期清單")
    public ReleaseSweepResult releaseExpiredClaims() {
        ReleaseSweepResult result = releases.sweep();
        audit.record(AdminAuditAction.RUN_RELEASE_SWEEP, null,
                "逾期 %d 筆，自動釋回 %d 筆".formatted(
                        result.overdueFound(), result.autoReleased()));
        return result;
    }
}
