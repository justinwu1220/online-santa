package com.onlinesanta.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** 市話（選填）：含區碼最多 10 碼，不支援分機。空白視為沒填。 */
@Documented
@Constraint(validatedBy = PhoneValidators.Landline.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface LandlinePhone {
    String message() default "聯絡電話格式不正確，請含區碼（例：04-2234-5678），不支援分機";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
