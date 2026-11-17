package com.onlinesanta.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.auth.AuthDtos.ForgotPasswordRequest;
import com.onlinesanta.auth.AuthDtos.LoginRequest;
import com.onlinesanta.auth.AuthDtos.RegisterRequest;
import com.onlinesanta.auth.AuthDtos.ResetPasswordRequest;
import com.onlinesanta.auth.AuthDtos.SessionView;
import com.onlinesanta.auth.AuthDtos.VerifyEmailRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 註冊、登入、登入狀態、信箱驗證與重設密碼。
 *
 * <p>登入狀態分兩段：短效的 access token 放在回應內文（前端只放記憶體），長效的 refresh token
 * 只放在 HttpOnly cookie（JavaScript 讀不到，XSS 偷不走）。
 *
 * <p>cookie 叫 {@code __session} 是刻意的：Firebase Hosting 轉發到 Cloud Run 時只會放行
 * 這個名字的 cookie，換別的名字到了後端就消失了。
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "帳號驗證", description = "註冊、登入、信箱驗證碼、重設密碼")
public class AuthController {

    /** Firebase Hosting 唯一會轉發的 cookie 名稱。 */
    public static final String REFRESH_COOKIE = "__session";
    static final String COOKIE_PATH = "/api/auth";

    private final LocalAuthService auth;
    private final CurrentUserService currentUser;
    private final AuthProperties properties;

    public AuthController(LocalAuthService auth, CurrentUserService currentUser, AuthProperties properties) {
        this.auth = auth;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    @PostMapping("/register")
    @Operation(summary = "註冊（認領者）並直接登入",
            description = "建立帳號、記錄須知同意並寄出 6 位數信箱驗證碼。機構同樣先以一般帳號註冊，再到機構申請頁送出申請")
    public ResponseEntity<SessionView> register(@Valid @RequestBody RegisterRequest request) {
        return withSession(auth.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "以信箱與密碼登入")
    public ResponseEntity<SessionView> login(@Valid @RequestBody LoginRequest request) {
        return withSession(auth.login(request.email(), request.password()), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    @Operation(summary = "用登入狀態 cookie 換新的 access token",
            description = "同時輪替 cookie。已撤銷的 cookie 又被使用時，整串登入狀態一併撤銷")
    public ResponseEntity<SessionView> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin) {
        requireAllowedOrigin(origin);
        return withSession(auth.refresh(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/logout")
    @Operation(summary = "登出", description = "撤銷登入狀態並清除 cookie；沒登入也回成功")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin) {
        requireAllowedOrigin(origin);
        auth.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "輸入信箱驗證碼", description = "需登入；驗證碼綁定目前登入的使用者")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        auth.verifyEmail(currentUser.require().userId(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verification/resend")
    @Operation(summary = "重新寄送信箱驗證碼", description = "需登入；兩封之間至少間隔 60 秒，一天最多 5 封")
    public ResponseEntity<Void> resendVerification() {
        auth.resendVerificationCode(currentUser.require().userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "申請重設密碼",
            description = "一律回 204，不透露這個信箱有沒有註冊過")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        auth.forgotPassword(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    @Operation(summary = "用信中連結的 token 設定新密碼",
            description = "成功後所有裝置的登入狀態都會被撤銷，需要重新登入")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        auth.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<SessionView> withSession(LocalAuthService.Session session, HttpStatus status) {
        SessionView body = new SessionView(session.access().value(), session.access().expiresInSeconds(),
                session.email(), session.emailVerified());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie(session.refreshToken(), properties.refreshTokenTtl()).toString())
                .body(body);
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                // Lax：跨站的 POST 不會帶這個 cookie，是 CSRF 的第一道防線；第二道是下面的 Origin 檢查
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    /**
     * 會用到 cookie 的端點（換發、登出）要求請求來源是我們自己的前端。
     * 沒帶 Origin（curl、同源的舊式請求）放行——那種情況本來就拿不到別人的 cookie。
     */
    private void requireAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return;
        }
        if (properties.allowedOrigins() == null || !properties.allowedOrigins().contains(origin)) {
            throw new AuthFailureException(HttpStatus.FORBIDDEN, "ORIGIN_NOT_ALLOWED", "不允許的請求來源");
        }
    }
}
