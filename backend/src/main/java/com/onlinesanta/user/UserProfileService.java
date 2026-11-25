package com.onlinesanta.user;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.changelog.ProfileChangeActor;
import com.onlinesanta.changelog.ProfileChangeLogService;
import com.onlinesanta.changelog.ProfileChangeTargetType;
import com.onlinesanta.changelog.ProfileSnapshots;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.user.dto.UserProfileUpdateRequest;
import com.onlinesanta.user.dto.UserProfileView;

/**
 * 個人檔案設定頁的後端邏輯。
 *
 * <p>任何已登入使用者（含信箱未驗證）都可以編輯自己的個人檔案——這不是會產生實質
 * 後果或牽涉他人的操作，不需要比照認領、機構申請那樣要求信箱已驗證。
 */
@Service
public class UserProfileService {

    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final ProfileChangeLogService changeLog;
    private final ApplicationEventPublisher events;

    public UserProfileService(UserRepository users, CurrentUserService currentUser,
                              ProfileChangeLogService changeLog, ApplicationEventPublisher events) {
        this.users = users;
        this.currentUser = currentUser;
        this.changeLog = changeLog;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public UserProfileView getMine() {
        AppPrincipal principal = currentUser.require();
        return UserProfileView.from(loadUser(principal.userId()), principal.emailVerified());
    }

    /**
     * 更新個人檔案，並留下異動紀錄（誰、什麼時候、哪個欄位從什麼改成什麼）。
     *
     * <p>紀錄與改動在同一個交易裡：rollback 掉的改動不該留下一筆說它發生過的紀錄。
     * 不需要 flush——recorder 比對的是記憶體裡的狀態。
     */
    @Transactional
    public UserProfileView updateMine(UserProfileUpdateRequest request) {
        AppPrincipal principal = currentUser.require();
        User user = loadUser(principal.userId());

        Map<String, String> before = ProfileSnapshots.of(user);
        String previousName = user.getDisplayName();
        user.updateProfile(request.displayName(), request.name(), request.phone());
        changeLog.record(ProfileChangeTargetType.USER, user.getId(),
                before, ProfileSnapshots.of(user), ProfileChangeActor.selfService(principal));

        // 榜上寫的是顯示名稱：公開顯示的人改了名，名單也得重算
        if (user.isLeaderboardOptIn() && !user.getDisplayName().equals(previousName)) {
            events.publishEvent(new LeaderboardNameChangedEvent(user.getId()));
        }

        return UserProfileView.from(user, principal.emailVerified());
    }

    /** 記錄看完首次登入說明。冪等：看過的人再呼叫不會改變時間。 */
    @Transactional
    public UserProfileView markOnboardedMine() {
        AppPrincipal principal = currentUser.require();
        User user = loadUser(principal.userId());
        user.markOnboarded(Instant.now());
        return UserProfileView.from(user, principal.emailVerified());
    }

    /**
     * 設定要不要在排行榜公開顯示名字。
     *
     * <p>不寫個資異動紀錄：這是使用者自己的顯示偏好，比照 {@code onboardedAt}
     * （見 {@link ProfileSnapshots}）不收進快照。
     */
    @Transactional
    public UserProfileView setLeaderboardOptInMine(boolean optIn) {
        AppPrincipal principal = currentUser.require();
        User user = loadUser(principal.userId());
        user.setLeaderboardOptIn(optIn, Instant.now());
        // 名單是整份快取的，不丟掉的話切換之後最多五分鐘榜上還是舊名字
        events.publishEvent(new LeaderboardNameChangedEvent(user.getId()));
        return UserProfileView.from(user, principal.emailVerified());
    }

    private User loadUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("使用者", userId));
    }
}
