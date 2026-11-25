package com.onlinesanta.user.dto;

import com.onlinesanta.user.User;

/**
 * 個人檔案設定頁使用的自身資料。
 *
 * <p>displayName 註冊時若沒填，會用信箱 {@code @} 前面那段暫代（見
 * {@link com.onlinesanta.auth.LocalAuthService}），照實顯示即可，
 * 使用者自己會在這一頁改成想用的名稱。
 */
public record UserProfileView(
        String displayName,
        String name,
        String phone,
        String email,
        boolean emailVerified,
        /** 已看完首次登入說明；false 的新帳號在信箱驗證完成後會跳出說明彈窗 */
        boolean onboarded,

        /** 在排行榜公開顯示名字；預設 false，榜上顯示「匿名的聖誕老人」 */
        boolean leaderboardOptIn) {

    public static UserProfileView from(User user, boolean emailVerified) {
        return new UserProfileView(
                user.getDisplayName(), user.getName(), user.getPhone(), user.getEmail(), emailVerified,
                user.isOnboarded(), user.isLeaderboardOptIn());
    }
}
