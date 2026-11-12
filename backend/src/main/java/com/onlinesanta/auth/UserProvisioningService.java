package com.onlinesanta.auth;

import java.time.Instant;
import java.util.Map;

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
    private final ProfileChangeLogService changeLog;

    public UserProvisioningService(UserRepository users, AuthProperties properties,
                                   ProfileChangeLogService changeLog) {
        this.users = users;
        this.properties = properties;
        this.changeLog = changeLog;
    }

    /**
     * <p>這裡有三處會在使用者沒有按下任何按鈕的情況下改動帳號資料：改綁 uid、修補顯示
     * 名稱、白名單提升為管理員。全部包進異動紀錄，{@code source=SYSTEM}。
     *
     * <p>這支每個 request 都會跑，但 recorder 沒有變動就不寫，所以代價只是每個 request
     * 多比對一份六個欄位的快照；實際上每個帳號最多各留一筆。
     *
     * <p>紀錄的 actor 必須由這裡明確傳入，不能讓 recorder 去讀 CurrentUserService——
     * 此刻 principal 還在建構中，SecurityContext 是空的。
     */
    @Transactional
    public AppPrincipal resolve(String firebaseUid, String email,
                                boolean emailVerified, String displayName) {
        User user = users.findByFirebaseUid(firebaseUid)
                .orElseGet(() -> adoptOrCreate(firebaseUid, email, emailVerified, displayName));

        if (user.isDisabled()) {
            throw new IdentityRejectedException(
                    HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "此帳號已被停用");
        }

        Map<String, String> before = ProfileSnapshots.of(user);
        user.repairLegacyDisplayName(displayName);
        promoteToAdminIfWhitelisted(user, emailVerified);
        changeLog.record(ProfileChangeTargetType.USER, user.getId(),
                before, ProfileSnapshots.of(user), ProfileChangeActor.system(user));

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
                    // 改綁 uid 是「同一個本地帳號換了一組 Firebase 身分」——也就是這段
                    // javadoc 警告的那條接管路徑。單獨記一筆，因為它發生在下面那個
                    // 快照之前，而且萬一這條路被濫用，這就是唯一的證據。
                    Map<String, String> beforeLink = ProfileSnapshots.of(existing);
                    existing.linkFirebaseUid(firebaseUid);
                    changeLog.record(ProfileChangeTargetType.USER, existing.getId(),
                            beforeLink, ProfileSnapshots.of(existing),
                            ProfileChangeActor.system(existing));
                    return existing;
                })
                .orElseGet(() -> users.save(User.newDonor(
                        firebaseUid, email, resolveDisplayName(displayName, email))));
    }

    /**
     * JIT 建立帳號時決定 displayName：優先用 token 的 name claim（Google 登入本來就有），
     * 沒有的話退而求其次用信箱 {@code @} 前面那段——比整串信箱好看，但終究是猜的，
     * 拿不到局部字串（例如信箱格式怪異）才真的保底用整個信箱。
     */
    private static String resolveDisplayName(String displayName, String email) {
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        String localPart = localPartOf(email);
        return StringUtils.hasText(localPart) ? localPart : email;
    }

    private static String localPartOf(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : null;
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
