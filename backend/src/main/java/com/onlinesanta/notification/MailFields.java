package com.onlinesanta.notification;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.user.User;

/**
 * 組樣板欄位的共用小工具：稱呼、連結、寄送期限。各 listener 只管「查資料、挑欄位」，
 * 這些慣例（稱呼後備、連結長相、過期期限不顯示）統一在這裡。
 */
public final class MailFields {

    /** 收信人沒有暱稱時的稱呼。 */
    public static final String DEFAULT_NAME = "聖誕老人";

    private MailFields() {
    }

    /** 成對給值（鍵、值、鍵、值…）。值可以是 null——樣板的 data-if 以 null 表示「省略該段」。 */
    public static Map<String, Object> of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("欄位必須成對給（鍵、值）");
        }
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    /**
     * 認領者／使用者的稱呼。用公開暱稱 {@code displayName}；
     * <strong>不用 {@code name}</strong>——那是開收據用的真實姓名，不可出現在任何通知。
     */
    public static String recipientName(User user) {
        String name = user == null ? null : user.getDisplayName();
        return name == null || name.isBlank() ? DEFAULT_NAME : name.strip();
    }

    public static String claimUrl(NotificationProperties properties, Claim claim) {
        return properties.publicUrl() + "/me/claims/" + claim.getId();
    }

    public static String orgUrl(NotificationProperties properties) {
        return properties.publicUrl() + "/org";
    }

    public static String orgSettingsUrl(NotificationProperties properties) {
        return properties.publicUrl() + "/org/settings";
    }

    /**
     * 「恢復寄送」信的期限欄位。
     *
     * <p>暫停時已經逾期的認領，恢復後是把暫停的時間加到「過去的日期」上，結果可能仍在過去。
     * 這時信上不能寫一個已經過了的「新的最晚寄出時間」，所以 {@code shipDeadline} 給 null
     * （樣板會省略那一段），改顯示 {@code deadlinePassed} 那段，請認領者到認領頁確認。
     */
    public static Map<String, Object> resumedDeadline(Claim claim, Instant now) {
        Instant deadline = claim.getShipDeadlineAt();
        boolean passed = deadline == null || !deadline.isAfter(now);
        return of("shipDeadline", passed ? null : NotificationFormat.dateTime(deadline),
                "deadlinePassed", passed);
    }

    public static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> more) {
        Map<String, Object> out = new HashMap<>(base);
        out.putAll(more);
        return out;
    }
}
