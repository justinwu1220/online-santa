package com.onlinesanta.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationFormatTest {

    @Test
    @DisplayName("剩餘時間：天加小時、只有小時、只有分鐘、不到一分鐘；一律捨去不進位")
    void remainingReadsLikeThePlannedExamples() {
        assertThat(NotificationFormat.remaining(Duration.ofHours(30))).isEqualTo("1 天 6 小時");
        assertThat(NotificationFormat.remaining(Duration.ofDays(2))).isEqualTo("2 天");
        assertThat(NotificationFormat.remaining(Duration.ofHours(3).plusMinutes(59))).isEqualTo("3 小時");
        assertThat(NotificationFormat.remaining(Duration.ofMinutes(20))).isEqualTo("20 分鐘");
        assertThat(NotificationFormat.remaining(Duration.ofSeconds(59))).isEqualTo("不到 1 分鐘");
        assertThat(NotificationFormat.remaining(Duration.ofSeconds(-5))).isEqualTo("不到 1 分鐘");
        // 23 小時 59 分不能寫成「1 天」
        assertThat(NotificationFormat.remaining(Duration.ofHours(23).plusMinutes(59))).isEqualTo("23 小時");
    }
}
