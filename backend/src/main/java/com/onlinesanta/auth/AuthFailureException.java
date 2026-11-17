package com.onlinesanta.auth;

import org.springframework.http.HttpStatus;

import com.onlinesanta.common.exception.ApiException;

/**
 * 登入、換發、限流這類帳號流程的失敗。帶自己的 HTTP 狀態與 errorCode，
 * 由 {@code GlobalExceptionHandler} 轉成 RFC 7807 回應，前端依 errorCode 顯示對應的訊息。
 */
public class AuthFailureException extends ApiException {

    public AuthFailureException(HttpStatus status, String errorCode, String message) {
        super(status, errorCode, message);
    }

    public static AuthFailureException unauthenticated() {
        return new AuthFailureException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "請先登入");
    }

    public static AuthFailureException tooManyRequests(String message) {
        return new AuthFailureException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", message);
    }
}
