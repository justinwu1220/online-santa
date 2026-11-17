package com.onlinesanta.auth;

import com.onlinesanta.terms.TermsType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code /api/auth/**} 的請求與回應。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 註冊時一併送出的須知同意（使用者在註冊頁勾選的版本）。 */
    public record Consent(@NotNull TermsType type, @NotBlank @Size(max = 50) String version) {
    }

    public record RegisterRequest(
            @NotBlank(message = "請填寫信箱")
            @Email(message = "信箱格式不正確")
            @Size(max = 255, message = "信箱長度不可超過 255 字")
            String email,

            @NotBlank(message = "請設定密碼")
            @Size(min = 8, max = 128, message = "密碼長度需為 8 到 128 個字元")
            String password,

            @Size(max = 100, message = "用戶名稱長度不可超過 100 字")
            String displayName,

            @Size(max = 100, message = "真實姓名長度不可超過 100 字")
            String name,

            @Valid Consent consent) {
    }

    public record LoginRequest(
            @NotBlank(message = "請填寫信箱") String email,
            @NotBlank(message = "請填寫密碼") String password) {
    }

    public record VerifyEmailRequest(
            @NotBlank(message = "請輸入驗證碼")
            @Pattern(regexp = "\\d{6}", message = "驗證碼是 6 位數字")
            String code) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "請填寫信箱")
            @Email(message = "信箱格式不正確")
            String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,

            @NotBlank(message = "請設定新密碼")
            @Size(min = 8, max = 128, message = "密碼長度需為 8 到 128 個字元")
            String newPassword) {
    }

    /**
     * 登入、註冊、換發成功的回應。refresh token 不在這裡——它只透過 HttpOnly cookie 傳遞，
     * 前端的 JavaScript 碰不到。
     *
     * @param expiresIn access token 距離過期的秒數
     */
    public record SessionView(String accessToken, long expiresIn, String email, boolean emailVerified) {
    }
}
