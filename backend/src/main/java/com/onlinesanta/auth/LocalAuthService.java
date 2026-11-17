package com.onlinesanta.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.onlinesanta.auth.AuthDtos.RegisterRequest;
import com.onlinesanta.common.exception.ApiException;
import com.onlinesanta.common.exception.BadRequestException;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.terms.TermsService;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

/**
 * 本機帳號流程：註冊、登入、換發登入狀態、信箱驗證碼、重設密碼。
 *
 * <p>幾個刻意的選擇：
 * <ul>
 *   <li><strong>會記錄失敗的方法用 {@code noRollbackFor = ApiException.class}。</strong>
 *       驗證碼猜錯的計數、密碼輸錯的鎖定、換發 token 被盜用時的整串撤銷，都是「丟出例外的同時
 *       必須留下的紀錄」，預設的 rollback 會把它們一起吃掉，等於防暴力破解形同虛設。</li>
 *   <li><strong>登入錯誤一律回同一個訊息。</strong>帳號不存在與密碼錯誤不可區分，
 *       連回應時間也盡量一致（帳號不存在時照樣跑一次密碼比對）。</li>
 *   <li><strong>忘記密碼永遠不透露信箱是否存在</strong>，連限流也是靜默略過，不回 429。</li>
 * </ul>
 */
@Service
public class LocalAuthService {

    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOGIN_LOCK = Duration.ofMinutes(15);

    static final int MAX_CODE_ATTEMPTS = 5;
    static final Duration CODE_TTL = Duration.ofMinutes(AuthMailService.CODE_TTL_MINUTES);
    static final Duration RESET_TTL = Duration.ofMinutes(AuthMailService.RESET_TTL_MINUTES);

    static final Duration MAIL_INTERVAL = Duration.ofSeconds(60);
    static final int MAX_MAILS_PER_DAY = 5;

    /** 同一張 refresh token 在這段時間內被重複使用，視為兩個分頁同時換發，不當成被盜用。 */
    static final Duration REFRESH_REUSE_GRACE = Duration.ofSeconds(10);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final AuthTokenRepository authTokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final AuthProperties properties;
    private final AuthMailService mail;
    private final TermsService terms;

    /** 帳號不存在時拿來做一次「假的」密碼比對，讓回應時間與密碼錯誤相近。 */
    private final String dummyHash;

    public LocalAuthService(UserRepository users, AuthTokenRepository authTokens,
                            RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                            AccessTokenService accessTokens, AuthProperties properties,
                            AuthMailService mail, TermsService terms) {
        this.users = users;
        this.authTokens = authTokens;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.properties = properties;
        this.mail = mail;
        this.terms = terms;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** 一次成功的登入：access token 給前端放記憶體，refresh token 由 controller 寫進 cookie。 */
    public record Session(AccessTokenService.IssuedToken access, String refreshToken, String email,
                          boolean emailVerified) {
    }

    // ------------------------------------------------------------------ 註冊與登入

    /**
     * 註冊並直接登入。建帳號、記錄須知同意、寄驗證碼在同一個交易裡完成。
     *
     * <p>信箱還沒驗證：帳號只有一般民眾的權限，要輸入驗證碼之後才能認領或申請機構。
     */
    @Transactional
    public Session register(RegisterRequest request) {
        String email = request.email().trim();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw emailTaken();
        }

        Instant now = Instant.now();
        User user = User.newDonor(email, UserAuthenticationService.resolveDisplayName(request.displayName(), email));
        user.updateProfile(user.getDisplayName(), request.name(), null);
        user.changePassword(passwordEncoder.encode(request.password()), now);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 兩個請求同時用同一個信箱註冊，輸的那個撞到 lower(email) 唯一索引
            throw emailTaken();
        }

        if (request.consent() != null) {
            terms.recordConsentOnRegister(user.getId(), request.consent().type(), request.consent().version());
        }
        issueVerificationCode(user);
        return openSession(user, UUID.randomUUID());
    }

    private static BusinessRuleException emailTaken() {
        return new BusinessRuleException("EMAIL_TAKEN", "這個信箱已經註冊過了，請直接登入");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session login(String rawEmail, String password) {
        Instant now = Instant.now();
        User user = users.findByEmailIgnoreCase(rawEmail.trim()).orElse(null);

        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            throw accountLocked();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            boolean lockedNow = user.recordFailedLogin(now, MAX_FAILED_LOGINS, LOGIN_LOCK);
            throw lockedNow ? accountLocked() : invalidCredentials();
        }
        // 密碼正確之後才說明帳號被停用，不讓人不知道密碼就能探測哪些信箱有帳號
        if (user.isDisabled()) {
            throw new AuthFailureException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "此帳號已被停用");
        }

        user.clearLoginFailures();
        return openSession(user, UUID.randomUUID());
    }

    private static AuthFailureException invalidCredentials() {
        return new AuthFailureException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "信箱或密碼不正確");
    }

    private static AuthFailureException accountLocked() {
        return new AuthFailureException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                "登入失敗次數過多，帳號暫時鎖定 15 分鐘。也可以用「忘記密碼」重設密碼");
    }

    // ------------------------------------------------------------------ 登入狀態（refresh token）

    /**
     * 用 refresh token 換新的 access token，同時輪替 refresh token。
     *
     * <p>已撤銷的 token 又被拿來用，代表 cookie 被複製了（偷的人和本人各拿著一張），
     * 整串一起撤銷，兩邊都得重新登入。唯一例外是剛撤銷不久（兩個分頁同時換發）。
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw AuthFailureException.unauthenticated();
        }
        Instant now = Instant.now();
        RefreshToken current = refreshTokens.findByTokenHash(sha256(rawToken))
                .orElseThrow(AuthFailureException::unauthenticated);

        if (current.isRevoked()) {
            if (!current.getRevokedAt().plus(REFRESH_REUSE_GRACE).isAfter(now)) {
                refreshTokens.revokeFamily(current.getFamilyId(), now);
            }
            throw AuthFailureException.unauthenticated();
        }
        if (current.isExpired(now)) {
            throw AuthFailureException.unauthenticated();
        }

        User user = users.findById(current.getUserId()).orElse(null);
        if (user == null || user.isDisabled()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw AuthFailureException.unauthenticated();
        }

        current.revoke(now);
        return openSession(user, current.getFamilyId());
    }

    /** 登出：撤銷這張 refresh token 所在的整串。token 不存在或已失效也視為成功。 */
    @Transactional
    public void logout(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return;
        }
        refreshTokens.findByTokenHash(sha256(rawToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    private Session openSession(User user, UUID familyId) {
        String refresh = newRandomToken();
        refreshTokens.save(new RefreshToken(user.getId(), familyId, sha256(refresh),
                Instant.now().plus(properties.refreshTokenTtl())));
        return new Session(accessTokens.issue(user.getId()), refresh, user.getEmail(), user.isEmailVerified());
    }

    // ------------------------------------------------------------------ 信箱驗證碼

    /** 重寄驗證碼。已驗證就什麼都不做。 */
    @Transactional
    public void resendVerificationCode(UUID userId) {
        User user = users.findById(userId).orElseThrow(AuthFailureException::unauthenticated);
        if (user.isEmailVerified()) {
            return;
        }
        if (isMailRateLimited(userId, AuthTokenPurpose.VERIFY_EMAIL)) {
            throw AuthFailureException.tooManyRequests("寄得太頻繁了，請稍等一分鐘再試；一天最多寄 5 封");
        }
        issueVerificationCode(user);
    }

    /** 產生新的驗證碼並寄出；同一個使用者先前還沒用掉的驗證碼立即作廢。 */
    private void issueVerificationCode(User user) {
        Instant now = Instant.now();
        authTokens.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(), AuthTokenPurpose.VERIFY_EMAIL)
                .forEach(old -> old.markUsed(now));

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        authTokens.save(new AuthToken(user.getId(), AuthTokenPurpose.VERIFY_EMAIL,
                codeHash(user.getId(), code), now.plus(CODE_TTL)));
        mail.sendVerificationCode(user.getEmail(), code);
    }

    /**
     * 驗證信箱。猜錯會累計次數，同一組驗證碼錯 {@value #MAX_CODE_ATTEMPTS} 次就作廢——
     * 6 位數只有一百萬種組合，沒有這個上限的話，登入後的人可以直接暴力猜。
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyEmail(UUID userId, String code) {
        User user = users.findById(userId).orElseThrow(AuthFailureException::unauthenticated);
        if (user.isEmailVerified()) {
            return;
        }

        Instant now = Instant.now();
        AuthToken token = authTokens
                .findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(userId, AuthTokenPurpose.VERIFY_EMAIL)
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new BadRequestException("CODE_EXPIRED",
                        "驗證碼已經失效，請重新寄送驗證碼"));
        if (token.getAttempts() >= MAX_CODE_ATTEMPTS) {
            throw codeLocked();
        }

        boolean matches = MessageDigest.isEqual(
                token.getTokenHash().getBytes(StandardCharsets.UTF_8),
                codeHash(userId, code).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            token.recordFailedAttempt();
            int remaining = MAX_CODE_ATTEMPTS - token.getAttempts();
            if (remaining <= 0) {
                throw codeLocked();
            }
            throw new BadRequestException("CODE_INVALID", "驗證碼不正確，還可以再試 %d 次".formatted(remaining));
        }

        token.markUsed(now);
        user.markEmailVerified(now);
    }

    private static BadRequestException codeLocked() {
        return new BadRequestException("CODE_LOCKED", "輸入錯誤次數過多，這組驗證碼已作廢，請重新寄送驗證碼");
    }

    // ------------------------------------------------------------------ 重設密碼

    /** 申請重設密碼。不論信箱存不存在、有沒有被限流，呼叫端看到的結果都一樣。 */
    @Transactional
    public void forgotPassword(String rawEmail) {
        User user = users.findByEmailIgnoreCase(rawEmail.trim()).orElse(null);
        if (user == null || isMailRateLimited(user.getId(), AuthTokenPurpose.RESET_PASSWORD)) {
            return;
        }

        Instant now = Instant.now();
        authTokens.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(), AuthTokenPurpose.RESET_PASSWORD)
                .forEach(old -> old.markUsed(now));

        String token = newRandomToken();
        authTokens.save(new AuthToken(user.getId(), AuthTokenPurpose.RESET_PASSWORD, sha256(token),
                now.plus(RESET_TTL)));
        mail.sendPasswordReset(user.getEmail(), token);
    }

    /**
     * 用重設連結裡的 token 設定新密碼。
     *
     * <p>成功後撤銷這個使用者所有登入狀態（密碼可能就是因為被盜才要重設的），並把信箱視為已驗證——
     * 收得到信就證明擁有這個信箱。
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = Instant.now();
        AuthToken token = authTokens.findByTokenHashAndPurpose(sha256(rawToken), AuthTokenPurpose.RESET_PASSWORD)
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new BadRequestException("TOKEN_INVALID",
                        "這個連結已經失效或使用過了，請重新申請重設密碼"));
        User user = users.findById(token.getUserId()).orElseThrow(() -> new BadRequestException("TOKEN_INVALID",
                "這個連結已經失效或使用過了，請重新申請重設密碼"));

        user.changePassword(passwordEncoder.encode(newPassword), now);
        user.markEmailVerified(now);
        token.markUsed(now);
        refreshTokens.revokeAllOf(user.getId(), now);
    }

    // ------------------------------------------------------------------ 排程清理

    /** 刪掉過期或用掉超過 7 天的一次性 token 與登入狀態。 */
    @Transactional
    public int[] cleanupStaleTokens() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        return new int[] {authTokens.deleteStale(cutoff), refreshTokens.deleteStale(cutoff)};
    }

    // ------------------------------------------------------------------ 共用

    /** 同一種信：距離上一封不到 60 秒，或 24 小時內已經寄滿 5 封。 */
    private boolean isMailRateLimited(UUID userId, AuthTokenPurpose purpose) {
        Instant now = Instant.now();
        boolean tooSoon = authTokens.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
                .map(last -> last.getCreatedAt().plus(MAIL_INTERVAL).isAfter(now))
                .orElse(false);
        long today = authTokens.countByUserIdAndPurposeAndCreatedAtAfter(userId, purpose,
                now.minus(Duration.ofDays(1)));
        return tooSoon || today >= MAX_MAILS_PER_DAY;
    }

    private static String newRandomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支援 SHA-256", e);
        }
    }

    /**
     * 驗證碼的雜湊：HMAC 而非單純的雜湊，並且綁定使用者 id。6 位數只有一百萬種組合，
     * 單純的 SHA-256 在資料庫外洩時可以瞬間離線破解；有密鑰就不行。
     */
    private String codeHash(UUID userId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(accessTokens.hmacKey());
            return HexFormat.of().formatHex(mac.doFinal((userId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("無法計算驗證碼雜湊", e);
        }
    }
}
