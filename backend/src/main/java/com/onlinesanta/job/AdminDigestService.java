package com.onlinesanta.job;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.onlinesanta.job.dto.AdminDigestResult;
import com.onlinesanta.notification.MailFields;
import com.onlinesanta.notification.MailTemplateId;
import com.onlinesanta.notification.NotificationFormat;
import com.onlinesanta.notification.NotificationProperties;
import com.onlinesanta.notification.NotificationService;

/**
 * 每日寄給管理員的待審摘要（通知 4.8）。
 *
 * <p><strong>誰會收到</strong>：資料庫中所有 {@code role = ADMIN}、未停用、信箱已驗證的使用者，
 * 一人一封，內容相同。不用 {@code APP_ADMIN_EMAILS}——正式站刻意不設，管理員是用 SQL 升級的。
 *
 * <p><strong>什麼時候不寄</strong>：四類待審總數為 0，整封信都不寄（不佔每日寄信額度）。
 * 只有部分類別是 0 時照寄，那一列顯示「0 件」與「—」。
 *
 * <p><strong>去重</strong>：{@code admin_digest_log} 以（台灣日期、管理員）為主鍵，先搶位再寄；
 * 寄失敗就刪掉位，下次排程再補。排程重試或手動多觸發都不會讓同一位管理員一天收到兩封。
 *
 * <p>查詢直接用 SQL 彙總，不載入實體：只要件數與最早的時間，一次排程只有幾個聚合查詢。
 * 同步寄出（理由同期限提醒：排程回應之後背景執行緒可能拿不到 CPU）。
 */
@Service
public class AdminDigestService {

    private static final Logger log = LoggerFactory.getLogger(AdminDigestService.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public AdminDigestService(JdbcTemplate jdbc, NotificationService notifications,
                              NotificationProperties properties) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.properties = properties;
    }

    /** 一類待審：件數與最久的那一件進入待審的時間（沒有則為 null）。 */
    record Queue(int count, Instant oldest) {
        String wait(Instant now) {
            return oldest == null ? "—" : NotificationFormat.remaining(Duration.between(oldest, now));
        }
    }

    public AdminDigestResult send() {
        return send(Instant.now());
    }

    AdminDigestResult send(Instant now) {
        Queue orgs = query("SELECT count(*), min(submitted_at) FROM organizations WHERE status = 'PENDING'");
        // 願望：取每個待審願望「最近一次送審」的事件時間；沒有事件（舊資料）就用願望建立時間
        Queue wishes = query("""
                SELECT count(*), min(t) FROM (
                    SELECT coalesce(
                        (SELECT max(e.created_at) FROM wish_events e
                          WHERE e.wish_id = w.id AND e.event_type IN ('SUBMITTED', 'RESUBMITTED')),
                        w.created_at) AS t
                    FROM wishes w WHERE w.status = 'PENDING_REVIEW') x
                """);
        Queue wishUnlocks = query("SELECT count(*), min(created_at) FROM unlock_requests "
                + "WHERE target_type = 'WISH' AND status = 'PENDING'");
        Queue orgUnlocks = query("SELECT count(*), min(created_at) FROM unlock_requests "
                + "WHERE target_type = 'ORGANIZATION' AND status = 'PENDING'");

        int total = orgs.count() + wishes.count() + wishUnlocks.count() + orgUnlocks.count();
        if (total == 0) {
            log.info("管理員每日摘要：沒有待審項目，略過。");
            return new AdminDigestResult(now, 0, 0, 0, 0, 0);
        }

        List<Map<String, Object>> admins = jdbc.queryForList("""
                SELECT id, email FROM users
                WHERE role = 'ADMIN' AND disabled = false AND email_verified_at IS NOT NULL
                ORDER BY email
                """);
        if (admins.isEmpty()) {
            log.warn("管理員每日摘要：有 {} 件待審，但資料庫裡沒有符合收信條件的管理員"
                    + "（ADMIN、未停用、信箱已驗證），沒有人會收到這封摘要。", total);
            return new AdminDigestResult(now, total, 0, 0, 0, 0);
        }

        LocalDate today = now.atZone(TAIPEI).toLocalDate();
        Map<String, Object> fields = MailFields.of(
                "asOf", NotificationFormat.dateTime(now),
                "totalCount", total,
                "orgCount", orgs.count(), "orgWait", orgs.wait(now),
                "wishCount", wishes.count(), "wishWait", wishes.wait(now),
                "wishUnlockCount", wishUnlocks.count(), "wishUnlockWait", wishUnlocks.wait(now),
                "orgUnlockCount", orgUnlocks.count(), "orgUnlockWait", orgUnlocks.wait(now),
                "orgQueueUrl", properties.publicUrl() + "/admin/orgs?filter=pending",
                "wishQueueUrl", properties.publicUrl() + "/admin/wishes?filter=review",
                "wishUnlockQueueUrl", properties.publicUrl() + "/admin/wishes?filter=frozen",
                "orgUnlockQueueUrl", properties.publicUrl() + "/admin/orgs?filter=suspended");

        int sent = 0;
        int alreadySent = 0;
        int failed = 0;
        for (Map<String, Object> admin : admins) {
            UUID userId = (UUID) admin.get("id");
            String email = (String) admin.get("email");
            // 先搶位：同時有兩個排程在跑，也只有一個搶得到
            int claimed = jdbc.update("INSERT INTO admin_digest_log (digest_date, user_id) VALUES (?, ?) "
                    + "ON CONFLICT DO NOTHING", java.sql.Date.valueOf(today), userId);
            if (claimed == 0) {
                alreadySent++;
                continue;
            }
            boolean ok = false;
            try {
                ok = notifications.sendTemplateNow(email, MailTemplateId.ADMIN_DIGEST, fields);
            } catch (Exception e) {
                log.warn("管理員每日摘要寄送時發生例外：收件人={}", email, e);
            }
            if (ok) {
                sent++;
            } else {
                failed++;
                // 沒寄成就放掉位子，下次排程再試
                jdbc.update("DELETE FROM admin_digest_log WHERE digest_date = ? AND user_id = ?",
                        java.sql.Date.valueOf(today), userId);
            }
        }

        log.info("管理員每日摘要：待審共 {} 件，收件管理員 {} 位，寄出 {} 封，今天已寄過 {} 位，失敗 {} 位",
                total, admins.size(), sent, alreadySent, failed);
        return new AdminDigestResult(now, total, admins.size(), sent, alreadySent, failed);
    }

    private Queue query(String sql) {
        return jdbc.queryForObject(sql, (rs, i) -> {
            Timestamp oldest = rs.getTimestamp(2);
            return new Queue(rs.getInt(1), oldest == null ? null : oldest.toInstant());
        });
    }
}
