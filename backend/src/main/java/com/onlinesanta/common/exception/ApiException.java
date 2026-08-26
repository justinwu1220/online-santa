package com.onlinesanta.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 所有預期內業務錯誤的基底。
 *
 * <p>每個子類自帶 HTTP 狀態與機器可讀的 errorCode，讓 {@code GlobalExceptionHandler}
 * 不需要為每種例外各寫一個 handler，也讓前端能依 errorCode 做精確處理
 * （例如認領衝突要顯示專屬提示，而非通用錯誤訊息）。
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
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
