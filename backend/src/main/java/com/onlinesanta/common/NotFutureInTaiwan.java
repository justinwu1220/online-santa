package com.onlinesanta.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * 日期不可晚於「台灣今天」。
 *
 * <p>為什麼不用 {@code @PastOrPresent}：它拿 JVM 的系統時區判斷「今天」，而 Cloud Run
 * 跑在 UTC。台灣時間 00:00～08:00 之間，使用者填的「今天」在 UTC 還是昨天，
 * 會被判成未來的日期而擋下，畫面上只剩一句「請求內容有誤」。全站的日期語意都以
 * 台灣時區為準（見 {@link TaiwanYear}），這裡同樣明確指定。
 *
 * <p>null 視為通過，必填性交給 {@code @NotNull}。
 */
@Documented
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotFutureInTaiwan.Validator.class)
public @interface NotFutureInTaiwan {

    String message() default "日期不可是未來的日期";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<NotFutureInTaiwan, LocalDate> {

        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            return value == null || !value.isAfter(LocalDate.now(TaiwanYear.ZONE));
        }
    }
}
