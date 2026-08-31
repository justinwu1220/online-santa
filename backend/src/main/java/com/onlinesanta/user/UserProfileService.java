package com.onlinesanta.user;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
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

    public UserProfileService(UserRepository users, CurrentUserService currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public UserProfileView getMine() {
        AppPrincipal principal = currentUser.require();
        return UserProfileView.from(loadUser(principal.userId()), principal.emailVerified());
    }

    @Transactional
    public UserProfileView updateMine(UserProfileUpdateRequest request) {
        AppPrincipal principal = currentUser.require();
        User user = loadUser(principal.userId());
        user.updateProfile(request.displayName(), request.phone());
        return UserProfileView.from(user, principal.emailVerified());
    }

    private User loadUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("使用者", userId));
    }
}
