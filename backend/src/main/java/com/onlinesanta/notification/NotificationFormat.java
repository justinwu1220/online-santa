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

    public static String dateTime(Instant instant) {
        return instant == null ? "—" : FORMATTER.format(instant);
    }
}
