package com.onlinesanta.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

/**
 * token 本身有效，但這個身分不被接受——例如帳號已停用，或信箱未驗證卻想接管既有帳號。
 *
 * <p>必須繼承 Spring Security 的 {@link AuthenticationException}：這些檢查發生在
 * 驗證階段，跑在 {@code GlobalExceptionHandler} 之外。丟一般的 RuntimeException 會直接
 * 穿過整條過濾鏈變成 500，使用者只看得到「伺服器錯誤」，完全不知道要去收驗證信。
 *
 * <p>由 {@link ApiAuthenticationEntryPoint} 轉成帶 errorCode 的 RFC 7807 回應，
 * 格式與其他錯誤一致。
 */
public class IdentityRejectedException extends AuthenticationException {

    private final transient HttpStatus status;
    private final String errorCode;

    public IdentityRejectedException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
