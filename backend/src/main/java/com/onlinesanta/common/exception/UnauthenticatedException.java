package com.onlinesanta.common.exception;

import org.springframework.http.HttpStatus;

/** 尚未識別出身分。M3 導入 Firebase Auth 後，多數情況會由 Spring Security 先攔下。 */
public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException() {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "請先登入");
    }
}
