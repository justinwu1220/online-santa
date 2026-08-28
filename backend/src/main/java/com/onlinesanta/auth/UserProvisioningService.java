package com.onlinesanta.auth;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;

/**
 * 把 Firebase 的身分對應到本地帳號（JIT provisioning）。
 *
 * <p>第一次登入時就地建立帳號，省去獨立的註冊流程——能通過 Firebase 驗證就代表
 * 身分已經確認過了。
 *
 * <p><strong>關於 {@code emailVerified}：</strong>這個類別有兩處原本依賴「email 一定
 * 驗證過」這個前提。只開放 Google 登入時前提成立（Google 保證），但一旦開放
 * email/密碼註冊就不成立了——Firebase 不會檢查註冊者是否真的擁有那個信箱。
 * 兩處都必須擋下未驗證的信箱，理由見各自的說明。
 */
@Service
public class UserProvisioningService {

    private final UserRepository users;
    private final AuthProperties properties;

    public UserProvisioningService(UserRepository users, AuthProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Transactional
    public AppPrincipal resolve(String firebaseUid, String email,
                                boolean emailVerified, String displayName) {
        User user = users.findByFirebaseUid(firebaseUid)
                .orElseGet(() -> adoptOrCreate(firebaseUid, email, emailVerified, displayName));

        if (user.isDisabled()) {
            throw new IdentityRejectedException(
                    HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "此帳號已被停用");
        }

        promoteToAdminIfWhitelisted(user, emailVerified);
        user.recordLogin(Instant.now());
        return AppPrincipal.from(user, emailVerified);
    }

    /**
     * 以 uid 找不到帳號時，用 email 找既有帳號並改綁新的 uid。
     *
     * <p>原意是處理「Firebase 端帳號被刪除後、以同一信箱重新註冊」——本地的認領紀錄
     * 應該延續給同一個人。
     *
     * <p><strong>但這條路徑必須要求信箱已驗證。</strong>否則任何人都能用某位機構成員的
     * 信箱註冊一個密碼帳號，一登入就直接接管對方的機構帳號。
     */
    private User adoptOrCreate(String firebaseUid, String email,
                               boolean emailVerified, String displayName) {
        return users.findByEmailIgnoreCase(email)
                .map(existing -> {
                    if (!emailVerified) {
                        throw new IdentityRejectedException(
                                HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                                "這個信箱已經有帳號。請先點擊驗證信中的連結，"
                                        + "確認你擁有這個信箱之後再登入");
                    }
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
     *
     * <p><strong>信箱未驗證時一律不提升。</strong>少了這個條件，任何人只要用白名單上的
     * 信箱註冊一個密碼帳號就能取得管理員權限——那是全站孩童資料、捐贈者個資與
     * 回饋照片的完整存取權。
     */
    private void promoteToAdminIfWhitelisted(User user, boolean emailVerified) {
        if (emailVerified
                && user.getRole() != UserRole.ADMIN
                && user.getOrganizationId() == null
                && properties.isAdminEmail(user.getEmail())) {
            user.promoteToAdmin();
        }
    }
}
