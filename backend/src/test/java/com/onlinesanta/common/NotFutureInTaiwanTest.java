package com.onlinesanta.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("日期不可晚於台灣今天")
class NotFutureInTaiwanTest {

    private final NotFutureInTaiwan.Validator validator = new NotFutureInTaiwan.Validator();

    private TimeZone original;

    @BeforeEach
    void rememberTimeZone() {
        original = TimeZone.getDefault();
    }

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(original);
    }

    @Test
    @DisplayName("台灣的今天與過去通過，明天不通過")
    void comparesAgainstTheTaiwanDate() {
        LocalDate today = LocalDate.now(TaiwanYear.ZONE);

        assertThat(validator.isValid(today, null)).isTrue();
        assertThat(validator.isValid(today.minusDays(1), null)).isTrue();
        assertThat(validator.isValid(today.plusDays(1), null)).isFalse();
    }

    @Test
    @DisplayName("null 通過，必填性交給 @NotNull")
    void nullIsLeftToNotNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("不受 JVM 時區影響：Cloud Run 跑在 UTC，台灣清晨使用者填的「今天」不能被擋")
    void isIndependentOfTheJvmTimeZone() {
        // 台灣 00:00～08:00，UTC 的日期還是前一天。用 @PastOrPresent 時，
        // 這段時間填「今天」會被判成未來而擋下
        for (String zone : new String[]{"UTC", "America/Los_Angeles", "Pacific/Kiritimati"}) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));

            LocalDate taiwanToday = LocalDate.now(TaiwanYear.ZONE);
            assertThat(validator.isValid(taiwanToday, null))
                    .as("JVM 時區 %s 下，台灣的今天要通過", zone).isTrue();
            assertThat(validator.isValid(taiwanToday.plusDays(1), null))
                    .as("JVM 時區 %s 下，台灣的明天要被擋", zone).isFalse();
        }
    }
}
