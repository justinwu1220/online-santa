package com.onlinesanta.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Email 通知的設定（{@code app.notification.*}）。
 *
 * @param host        SMTP 主機。留空代表尚未設定，{@link MailConfig} 不會建立
 *                    {@code JavaMailSender}，通知一律降級為 no-op（只記 log）
 * @param port        SMTP 連接埠
 * @param username    SMTP 帳號
 * @param password    SMTP 密碼
 * @param fromAddress 信件的寄件人地址
 * @param publicUrl   前端網站的網址，信件內文的連結靠它組出來——這支 API 自己的網址
 *                    使用者到不了。<strong>結尾的斜線會被去掉</strong>，見下方說明
 * @param dailyLimit  每日（UTC）最多寄出幾封，0 代表不限。寄信供應商（Resend）的額度是有限的，
 *                    而註冊、忘記密碼都會寄信——被機器人批次註冊，或測試環境出錯陷入迴圈，都可能
 *                    一天內燒光額度，連帶讓正式環境的驗證碼寄不出去。超過上限就不再寄，並記下可
 *                    告警的標記（見 {@link NotificationService#LIMIT_REACHED_MARKER}）
 * @param subjectPrefix 加在每封信主旨前面的文字，空白代表不加。測試站設成「[測試站]」，
 *                    收件人一眼就能分辨這不是正式站寄的
 */
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        String host,
        int port,
        String username,
        String password,
        String fromAddress,
        String publicUrl,
        int dailyLimit,
        String subjectPrefix) {

    /** 沒有每日上限、不加主旨前綴的設定（既有的測試與呼叫端沿用）。 */
    public NotificationProperties(String host, int port, String username, String password,
                                  String fromAddress, String publicUrl) {
        this(host, port, username, password, fromAddress, publicUrl, 0, "");
    }

    /**
     * 正規化 {@code publicUrl}。
     *
     * <p>各封信都是以 {@code publicUrl() + "/org"} 這種字串串接組出連結（目前共 17 處）。
     * 設定的值若帶著結尾斜線——從瀏覽器網址列複製下來最自然就是這樣——連結會變成
     * {@code https://example.org//org}。路徑上的雙斜線不會被瀏覽器修掉，而前端是
     * SPA：Firebase Hosting 把它 rewrite 到 {@code index.html} 之後，React Router
     * 看到的路徑是 {@code //org}，匹配不到 {@code /org} 這條路由。
     *
     * <p>也就是說一個結尾斜線就會讓<strong>每一封通知信裡的每一個連結</strong>都失效，
     * 而且只在真實收件匣裡看得出來。在這裡收斂比在 17 個地方各自小心可靠。
     */
    // 有兩個建構子時 Spring 不知道該用哪個綁定設定值，要明確指定
    @ConstructorBinding
    public NotificationProperties {
        publicUrl = normalizeBaseUrl(publicUrl);
        dailyLimit = Math.max(0, dailyLimit);
        subjectPrefix = subjectPrefix == null ? "" : subjectPrefix.strip();
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        int end = trimmed.length();
        while (end > 0 && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        return trimmed.substring(0, end);
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank();
    }
}
