package com.onlinesanta.notification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 通知信內文共用的時間格式，一律用台北時區——收信人看到的時間要跟他生活的時區一致。
 *
 * <p>{@code public}：{@code job} 套件的排程服務（例如寄送期限提醒）組信件內文時也要
 * 用同一份格式，不該各自重寫一份。
 */
public final class NotificationFormat {

    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.TAIWAN).withZone(ZONE);

    private NotificationFormat() {
    }

    /**
     * 剩餘時間的讀法：「1 天 6 小時」「3 小時」「20 分鐘」「不到 1 分鐘」。
     * 一律<strong>捨去</strong>而非進位——寫「剩 1 天」就不能其實只剩 23 小時。
     */
    public static String remaining(java.time.Duration duration) {
        long minutes = Math.max(0, duration.toMinutes());
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        if (days > 0) {
            return hours > 0 ? days + " 天 " + hours + " 小時" : days + " 天";
        }
        if (hours > 0) {
            return hours + " 小時";
        }
        return minutes > 0 ? minutes + " 分鐘" : "不到 1 分鐘";
    }

    public static String dateTime(Instant instant) {
        return instant == null ? "—" : FORMATTER.format(instant);
    }
}
