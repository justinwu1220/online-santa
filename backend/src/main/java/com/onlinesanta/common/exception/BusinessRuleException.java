package com.onlinesanta.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 請求本身格式正確，但違反了業務規則（例如對已認領的願望執行下架）。
 *
 * <p>採 409 而非 400：問題不在請求的語法，而在於資源目前的狀態與請求相衝突。
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }

    public BusinessRuleException(String message) {
        this("BUSINESS_RULE_VIOLATION", message);
    }
}
