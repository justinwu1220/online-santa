package com.onlinesanta.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 請求參數本身不合法（例如選填的月份超出 1–12 的範圍）。
 *
 * <p>與 {@link BusinessRuleException} 的差異：後者是「請求語法正確、但與資源目前狀態
 * 衝突」（409）；這裡是請求本身就不構成一個合法的查詢（400）。
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
