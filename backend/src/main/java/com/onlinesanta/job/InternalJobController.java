package com.onlinesanta.job;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.auth.LocalAuthService;
import com.onlinesanta.job.dto.AttachmentCleanupResult;
import com.onlinesanta.job.dto.AuthTokenCleanupResult;
import com.onlinesanta.job.dto.DeadlineReminderResult;
import com.onlinesanta.job.dto.ReleaseSweepResult;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * 由 Cloud Scheduler 定時呼叫的內部端點。
 *
 * <p>Cloud Run 是 scale-to-zero 的，沒有常駐的行程，{@code @Scheduled} 不會可靠地觸發
 * ——實例可能整天都不存在。因此改由外部排程打 HTTP 進來，順帶把實例喚醒。
 *
 * <p>存取控制見 {@code InternalJobSecurityConfig}：只接受指定服務帳號的 Google OIDC
 * token，與一般使用者的 access token 走完全不同的驗證鏈。
 */
@RestController
@RequestMapping("/internal/jobs")
@Hidden
public class InternalJobController {

    private final ClaimReleaseService releases;
    private final PendingAttachmentCleanupService attachmentCleanup;
    private final DeadlineReminderService deadlineReminders;
    private final LocalAuthService localAuth;
    private final AdminDigestService adminDigest;
    private final com.onlinesanta.closing.ClosingCleanupService closingCleanup;

    public InternalJobController(ClaimReleaseService releases,
                                 PendingAttachmentCleanupService attachmentCleanup,
                                 DeadlineReminderService deadlineReminders,
                                 LocalAuthService localAuth,
                                 AdminDigestService adminDigest,
                                 com.onlinesanta.closing.ClosingCleanupService closingCleanup) {
        this.closingCleanup = closingCleanup;
        this.releases = releases;
        this.attachmentCleanup = attachmentCleanup;
        this.deadlineReminders = deadlineReminders;
        this.localAuth = localAuth;
        this.adminDigest = adminDigest;
    }

    @PostMapping("/release-expired-claims")
    public ReleaseSweepResult releaseExpiredClaims() {
        return releases.sweep();
    }

    /** 每日寄給管理員的待審摘要（沒有待審項目時不寄）。 */
    @PostMapping("/send-admin-digest")
    public com.onlinesanta.job.dto.AdminDigestResult sendAdminDigest() {
        return adminDigest.send();
    }

    /** 清除過了暫存期限（預設 30 天）的收據 PDF 與匯出壓縮檔；資料庫裡的收據資料保留。 */
    @PostMapping("/cleanup-closing-files")
    public com.onlinesanta.job.dto.ClosingCleanupResult cleanupClosingFiles() {
        return closingCleanup.cleanup();
    }

    @PostMapping("/cleanup-pending-attachments")
    public AttachmentCleanupResult cleanupPendingAttachments() {
        return attachmentCleanup.cleanup();
    }

    /** 刪掉過期或用掉超過 7 天的驗證碼、重設密碼 token 與登入狀態。 */
    @PostMapping("/cleanup-auth-tokens")
    public AuthTokenCleanupResult cleanupAuthTokens() {
        int[] deleted = localAuth.cleanupStaleTokens();
        return new AuthTokenCleanupResult(deleted[0], deleted[1]);
    }

    @PostMapping("/send-deadline-reminders")
    public DeadlineReminderResult sendDeadlineReminders() {
        return deadlineReminders.sweep();
    }
}
