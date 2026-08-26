package com.onlinesanta.auth;

import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

/**
 * 開發環境的帳號 JIT 建立。比照 M3 Firebase 首次登入的行為：沒見過的 email
 * 就地建立 DONOR 帳號。
 *
 * <p>獨立成一個 bean 而非放在 filter 裡，是因為 {@code @Transactional} 需要經過
 * Spring 的代理才會生效——同類別內的自呼叫不會被攔截。
 */
@Service
@Profile("!prod")
public class DevUserProvisioner {

    private final UserRepository users;

    public DevUserProvisioner(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public AppPrincipal resolve(String email, String displayName) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseGet(() -> users.save(User.newDonor(
                        "dev-" + email,
                        email,
                        StringUtils.hasText(displayName) ? displayName : email)));
        user.recordLogin(Instant.now());
        return AppPrincipal.from(user);
    }
}
