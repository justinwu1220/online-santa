package com.onlinesanta.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link NotificationProperties} 的 {@code publicUrl} 正規化。
 *
 * <p>守的是一個只會在真實收件匣裡現形的錯誤：{@code APP_PUBLIC_URL} 帶著結尾斜線時，
 * 信裡的連結會變成 {@code https://example.org//org}，而 SPA 的路由匹配不到 {@code //org}。
 */
class NotificationPropertiesTest {

    private static String publicUrlOf(String configured) {
        return new NotificationProperties(null, 587, null, null, "from@example.org", configured)
                .publicUrl();
    }

    @Test
    @DisplayName("結尾斜線會被去掉——否則每封信的每個連結都會多一條斜線而失效")
    void stripsTrailingSlash() {
        assertThat(publicUrlOf("https://onlinesanta.example/")).isEqualTo("https://onlinesanta.example");
        assertThat(publicUrlOf("https://onlinesanta.example///")).isEqualTo("https://onlinesanta.example");
        assertThat(publicUrlOf("http://localhost:5173/")).isEqualTo("http://localhost:5173");
    }

    @Test
    @DisplayName("沒有結尾斜線時原樣保留，路徑中段的斜線不受影響")
    void leavesWellFormedValueAlone() {
        assertThat(publicUrlOf("https://onlinesanta.example")).isEqualTo("https://onlinesanta.example");
        assertThat(publicUrlOf("https://example.org/santa")).isEqualTo("https://example.org/santa");
    }

    @Test
    @DisplayName("前後空白會被去掉——環境變數貼進去時很容易多帶")
    void trimsWhitespace() {
        assertThat(publicUrlOf("  https://onlinesanta.example/  ")).isEqualTo("https://onlinesanta.example");
    }

    @Test
    @DisplayName("null 與空字串原樣傳下去，交給 MailConfig 判斷要不要擋")
    void keepsAbsentValueAbsent() {
        assertThat(publicUrlOf(null)).isNull();
        assertThat(publicUrlOf("   ")).isEmpty();
    }

    @Test
    @DisplayName("isConfigured 只看 host")
    void isConfiguredLooksAtHostOnly() {
        assertThat(new NotificationProperties(null, 587, null, null, null, null).isConfigured()).isFalse();
        assertThat(new NotificationProperties("  ", 587, null, null, null, null).isConfigured()).isFalse();
        assertThat(new NotificationProperties("smtp.example", 587, null, null, null, null).isConfigured()).isTrue();
    }
}
