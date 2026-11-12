package com.onlinesanta.changelog;

import java.util.UUID;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRole;

/**
 * 異動的操作者，由呼叫端明確傳入。
 *
 * <p>刻意不讓 recorder 自己去讀 {@code CurrentUserService}：登入時修補資料那條路徑跑在
 * 認證流程裡，當下 principal 還在建構中，根本沒有 SecurityContext 可讀。
 *
 * @param userId 操作者；系統自動改動時為 null
 * @param email  寫入當下的 email（去正規化保存）
 * @param role   當下「生效」的角色，用 {@link AppPrincipal#effectiveRole()}——授權是照它判斷的，
 *               證據就該說操作者當時被允許是什麼身分，否則會跟放行的那道檢查互相矛盾
 */
public record ProfileChangeActor(UUID userId, String email, UserRole role, ProfileChangeSource source) {

    /** 本人（或機構成員）在自己的設定頁改的。 */
    public static ProfileChangeActor selfService(AppPrincipal principal) {
        return new ProfileChangeActor(principal.userId(), principal.email(), principal.effectiveRole(),
                ProfileChangeSource.SELF_SERVICE);
    }

    /** 管理員代為改動他人的資料。 */
    public static ProfileChangeActor admin(AppPrincipal principal) {
        return new ProfileChangeActor(principal.userId(), principal.email(), principal.effectiveRole(),
                ProfileChangeSource.ADMIN);
    }

    /**
     * 系統自動改動。仍然記下「被改的那個帳號」當作 actor，因為那是唯一與這次改動有關的
     * 身分；靠 {@code source=SYSTEM} 區分出「沒有人按下送出」。
     */
    public static ProfileChangeActor system(User user) {
        return new ProfileChangeActor(user.getId(), user.getEmail(), user.getRole(),
                ProfileChangeSource.SYSTEM);
    }
}
