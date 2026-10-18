package com.onlinesanta.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** 行動電話：09 開頭 10 碼（可帶 dash 與空白）。空值交給 {@code @NotBlank} 判斷，這裡不管。 */
@Documented
@Constraint(validatedBy = PhoneValidators.Mobile.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MobilePhone {
    String message() default "行動電話格式不正確，請填 09 開頭的 10 碼（例：0912-345-678）";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
