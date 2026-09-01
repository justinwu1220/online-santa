package com.onlinesanta.notification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 通知信內文共用的時間格式，一律用台北時區——收信人看到的時間要跟他生活的時區一致。 */
final class NotificationFormat {

    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.TAIWAN).withZone(ZONE);

    private NotificationFormat() {
    }

    static String dateTime(Instant instant) {
        return instant == null ? "—" : FORMATTER.format(instant);
    }
}
