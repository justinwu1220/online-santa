package com.onlinesanta.common.validation;

import com.onlinesanta.common.PhoneFormat;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

final class PhoneValidators {

    private PhoneValidators() {
    }

    static final class Mobile implements ConstraintValidator<MobilePhone, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || PhoneFormat.mobile(value).isPresent();
        }
    }

    static final class Landline implements ConstraintValidator<LandlinePhone, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || PhoneFormat.landline(value).isPresent();
        }
    }
}
