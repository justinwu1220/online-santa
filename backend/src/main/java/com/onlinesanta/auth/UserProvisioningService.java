package com.onlinesanta.auth;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.onlinesanta.common.exception.ForbiddenException;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;

/**
 * 把 Firebase 的身分對應到本地帳號（JIT provisioning）。
 *
 * <p>第一次登入時就地建立帳號，省去獨立的註冊流程——能通過 Firebase 驗證就代表
 * 身分已經確認過了。
 */
@Service
public class UserProvisioningService {

    private final UserRepository users;
    private final AuthProperties properties;

    public UserProvisioningService(UserRepository users, AuthProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    /**
     * 以 Firebase uid 為主鍵尋找帳號，找不到就建立。
     *
     * <p>uid 而非 email 才是穩定識別碼（使用者可以更換 email）。但若遇到 email 相同、
     * uid 不同的既有帳號——例如 Firebase 端刪除後以同一信箱重建——則把既有帳號改綁
     * 新的 uid，而不是讓 email 的唯一索引噴錯。
     */
    @Transactional
    public AppPrincipal resolve(String firebaseUid, String email, String displayName) {
        User user = users.findByFirebaseUid(firebaseUid)
                .orElseGet(() -> adoptOrCreate(firebaseUid, email, displayName));

        if (user.isDisabled()) {
            throw new ForbiddenException("ACCOUNT_DISABLED", "此帳號已被停用");
        }

        promoteToAdminIfWhitelisted(user);
        user.recordLogin(Instant.now());
        return AppPrincipal.from(user);
    }

    private User adoptOrCreate(String firebaseUid, String email, String displayName) {
        return users.findByEmailIgnoreCase(email)
                .map(existing -> {
                    existing.linkFirebaseUid(firebaseUid);
                    return existing;
                })
                .orElseGet(() -> users.save(User.newDonor(
                        firebaseUid,
                        email,
                        StringUtils.hasText(displayName) ? displayName : email)));
    }

    /**
     * 白名單內的 email 取得 ADMIN 角色。
     *
     * <p>每次登入都檢查，因此把 email 加進設定檔後不必手動改資料庫。已隸屬機構的
     * 帳號不會被提升——那會讓同一人既能上架願望又能審核自己的機構。
     */
    private void promoteToAdminIfWhitelisted(User user) {
        if (user.getRole() != UserRole.ADMIN
                && user.getOrganizationId() == null
                && properties.isAdminEmail(user.getEmail())) {
            user.promoteToAdmin();
        }
    }
}
