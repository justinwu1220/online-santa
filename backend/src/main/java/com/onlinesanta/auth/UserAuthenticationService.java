package com.onlinesanta.auth;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.onlinesanta.changelog.ProfileChangeActor;
import com.onlinesanta.changelog.ProfileChangeLogService;
import com.onlinesanta.changelog.ProfileChangeTargetType;
import com.onlinesanta.changelog.ProfileSnapshots;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;

/**
 * 把通過驗證的請求（token 裡的使用者 id）轉成本系統的身分。
 *
 * <p>每個請求都會跑一次，回資料庫取最新的帳號狀態：停用、角色、信箱是否驗證都即時生效。
 *
 * <p><strong>關於信箱驗證：</strong>管理員白名單提升必須要求信箱已驗證。少了這個條件，
 * 任何人用白名單上的信箱註冊一個密碼帳號，就能取得全站孩童資料與捐贈者個資的完整存取權。
 */
@Service
public class UserAuthenticationService {

    private final UserRepository users;
    private final AuthProperties properties;
    private final ProfileChangeLogService changeLog;

    public UserAuthenticationService(UserRepository users, AuthProperties properties,
                                     ProfileChangeLogService changeLog) {
        this.users = users;
        this.properties = properties;
        this.changeLog = changeLog;
    }

    /**
     * 載入使用者並建立這個請求的身分。
     *
     * <p>白名單提升為管理員會在使用者沒有按任何按鈕的情況下改動帳號，因此寫進異動紀錄
     * （{@code source=SYSTEM}）。紀錄的 actor 必須由這裡明確傳入，不能讓 recorder 去讀
     * CurrentUserService——此刻 principal 還在建構中，SecurityContext 是空的。
     */
    @Transactional
    public AppPrincipal principalFor(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new IdentityRejectedException(
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "請先登入"));
        return principalOf(user);
    }

    /**
     * 開發模式（{@code X-Dev-User-Email} 標頭）：沒有這個信箱的帳號就建一個，並照標頭設定
     * 信箱是否已驗證。只由 {@link DevPrincipalFilter} 呼叫。
     */
    @Transactional
    public AppPrincipal resolveForDev(String email, boolean verified, String displayName) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseGet(() -> users.save(User.newDonor(email, resolveDisplayName(displayName, email))));
        if (verified) {
            user.markEmailVerified(Instant.now());
        } else {
            user.markEmailUnverified();
        }
        return principalOf(user);
    }

    private AppPrincipal principalOf(User user) {
        if (user.isDisabled()) {
            throw new IdentityRejectedException(
                    HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "此帳號已被停用");
        }

        Map<String, String> before = ProfileSnapshots.of(user);
        promoteToAdminIfWhitelisted(user);
        changeLog.record(ProfileChangeTargetType.USER, user.getId(),
                before, ProfileSnapshots.of(user), ProfileChangeActor.system(user));

        user.recordLogin(Instant.now());
        return AppPrincipal.from(user, user.isEmailVerified());
    }

    /**
     * 註冊時決定 displayName：優先用使用者填的，沒有的話退而求其次用信箱 {@code @} 前面那段
     * ——比整串信箱好看，但終究是猜的，拿不到局部字串才真的保底用整個信箱。
     */
    static String resolveDisplayName(String displayName, String email) {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        int at = email == null ? -1 : email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /**
     * 白名單內的 email 取得 ADMIN 角色。
     *
     * <p>每次請求都檢查，因此把 email 加進設定檔後不必手動改資料庫。已隸屬機構的
     * 帳號不會被提升——那會讓同一人既能上架願望又能審核自己的機構。信箱未驗證時一律不提升。
     */
    private void promoteToAdminIfWhitelisted(User user) {
        if (user.isEmailVerified()
                && user.getRole() != UserRole.ADMIN
                && user.getOrganizationId() == null
                && properties.isAdminEmail(user.getEmail())) {
            user.promoteToAdmin();
        }
    }
}
