package com.onlinesanta.job;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.job.dto.AttachmentCleanupResult;
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
 * token，與一般使用者的 Firebase token 走完全不同的驗證鏈。
 */
@RestController
@RequestMapping("/internal/jobs")
@Hidden
public class InternalJobController {

    private final ClaimReleaseService releases;
    private final PendingAttachmentCleanupService attachmentCleanup;
    private final DeadlineReminderService deadlineReminders;

    public InternalJobController(ClaimReleaseService releases,
                                 PendingAttachmentCleanupService attachmentCleanup,
                                 DeadlineReminderService deadlineReminders) {
        this.releases = releases;
        this.attachmentCleanup = attachmentCleanup;
        this.deadlineReminders = deadlineReminders;
    }

    @PostMapping("/release-expired-claims")
    public ReleaseSweepResult releaseExpiredClaims() {
        return releases.sweep();
    }

    @PostMapping("/cleanup-pending-attachments")
    public AttachmentCleanupResult cleanupPendingAttachments() {
        return attachmentCleanup.cleanup();
    }

    @PostMapping("/send-deadline-reminders")
    public DeadlineReminderResult sendDeadlineReminders() {
        return deadlineReminders.sweep();
    }
}
