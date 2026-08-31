package com.onlinesanta.user.dto;

import com.onlinesanta.user.User;

/**
 * 個人檔案設定頁使用的自身資料。
 *
 * <p>displayName 是 JIT provisioning 時可能以 email 帶入的舊資料
 * （見 {@link com.onlinesanta.auth.UserProvisioningService}），照實顯示即可，
 * 使用者自己會在這一頁改成想用的名稱。
 */
public record UserProfileView(
        String displayName,
        String phone,
        String email,
        boolean emailVerified) {

    public static UserProfileView from(User user, boolean emailVerified) {
        return new UserProfileView(
                user.getDisplayName(), user.getPhone(), user.getEmail(), emailVerified);
    }
}
