package com.onlinesanta.common.exception;

import org.springframework.http.HttpStatus;

/** 身分已知，但無權執行此操作。 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public ForbiddenException(String errorCode, String message) {
        super(HttpStatus.FORBIDDEN, errorCode, message);
    }
}
