package com.onlinesanta.organization.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.util.StringUtils;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * 聯絡電話與行動電話至少要填一個。
 *
 * <p>這是專案第一個自訂的 Bean Validation 約束。沒有照 Organization.updateReleasePolicy
 * 的前例在 Service 裡用 if 擋，是因為那條是領域不變量（排程任務真的依賴兩欄一致），
 * 而這條是輸入驗證：BusinessRuleException 回 409、BadRequestException 回 400，但兩者
 * 都不帶 fieldErrors，使用者只會看到頁面頂端的通用橫幅，看不出是哪一格要補。
 *
 * <p><strong>關鍵在 addPropertyNode("contactPhone")。</strong>類別層級約束預設產生的是
 * path 為空的 ObjectError，而 GlobalExceptionHandler 只讀 getFieldErrors()——全域錯誤
 * 會被整個丟掉，前端只收到一個 fieldErrors 為空的 400。掛上 property node 之後 Spring
 * 會轉成 path = contactPhone 的真 FieldError，直接落進既有的 fieldErrors map，
 * 前端原本就寫好的 error={fieldErrors?.contactPhone} 一行都不用改。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AtLeastOnePhone.Validator.class)
public @interface AtLeastOnePhone {

    String message() default "請至少填寫一種聯絡電話（聯絡電話或行動電話）";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 被約束的 DTO 要實作的最小介面。刻意用介面而不是反射撈欄位——record 的存取子
     * 名稱直接滿足這兩個方法，編譯期就綁定；哪天有人把這個註解貼到沒實作介面的型別上，
     * Hibernate Validator 會丟 UnexpectedTypeException，而不是安靜地不檢查。
     */
    interface Phones {
        String contactPhone();

        String mobilePhone();
    }

    class Validator implements ConstraintValidator<AtLeastOnePhone, Phones> {

        @Override
        public boolean isValid(Phones phones, ConstraintValidatorContext context) {
            // 整個物件為 null 不是這條規則的事，交給 @NotNull
            if (phones == null) {
                return true;
            }
            if (StringUtils.hasText(phones.contactPhone())
                    || StringUtils.hasText(phones.mobilePhone())) {
                return true;
            }
            // 關掉預設那個 path 為空的違規，只留掛在 contactPhone 上的那一個，
            // 否則會同時產生一個註定被 GlobalExceptionHandler 丟掉的全域錯誤
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("contactPhone")
                    .addConstraintViolation();
            return false;
        }
    }
}
