package com.onlinesanta.common.exception;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 統一以 RFC 7807 {@link ProblemDetail} 回覆錯誤。
 *
 * <p>除了標準欄位外一律附上 {@code errorCode}，讓前端能針對特定情境做處理
 * （例如搶領時的 WISH_ALREADY_CLAIMED 要顯示「剛被別人領走了」而非通用錯誤）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI TYPE_BASE = URI.create("https://onlinesanta.dev/problems/");

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        // 預期內的業務錯誤，不需要 stack trace 汙染日誌
        log.debug("業務錯誤 [{}] {}", ex.getErrorCode(), ex.getMessage());
        return problem(ex.getStatus(), ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("未預期的錯誤", ex);
        // 不把內部細節洩漏給呼叫端
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "系統發生未預期的錯誤", "INTERNAL_ERROR");
    }

    /** 覆寫 Bean Validation 的預設回應，補上逐欄位的錯誤訊息。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "請求內容有誤", "VALIDATION_FAILED");
        body.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    private ProblemDetail problem(HttpStatus status, String detail, String errorCode) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(TYPE_BASE.resolve(errorCode.toLowerCase().replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("errorCode", errorCode);
        return problem;
    }
}
