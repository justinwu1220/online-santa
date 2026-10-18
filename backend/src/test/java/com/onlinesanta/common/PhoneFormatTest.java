package com.onlinesanta.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("電話格式（SPEC 13.4）")
class PhoneFormatTest {

    @Test
    @DisplayName("行動電話：09 開頭 10 碼，自動加 dash；貼上的括號與空白會整理好")
    void mobile() {
        assertThat(PhoneFormat.mobile("0912345678")).contains("0912-345-678");
        assertThat(PhoneFormat.mobile("0912-345-678")).contains("0912-345-678");
        assertThat(PhoneFormat.mobile("0912 345 678")).contains("0912-345-678");
        assertThat(PhoneFormat.mobile("091234567")).isEmpty();
        assertThat(PhoneFormat.mobile("0812345678")).isEmpty();
        assertThat(PhoneFormat.mobile("0912-345-67a")).isEmpty();
        assertThat(PhoneFormat.mobile(null)).isEmpty();
    }

    @Test
    @DisplayName("市話：依區碼規則切成「區碼-前段-後 4 碼」")
    void landline() {
        assertThat(PhoneFormat.landline("0222345678")).contains("02-2234-5678");
        assertThat(PhoneFormat.landline("(04) 2234 5678")).contains("04-2234-5678");
        assertThat(PhoneFormat.landline("0312345678")).contains("03-1234-5678");
        assertThat(PhoneFormat.landline("031234567")).contains("03-123-4567");
        assertThat(PhoneFormat.landline("049123456")).contains("049-12-3456");
        assertThat(PhoneFormat.landline("0371234567")).contains("037-123-4567");
        assertThat(PhoneFormat.landline("0826123456")).contains("0826-12-3456");
        assertThat(PhoneFormat.landline("0836123456")).contains("0836-12-3456");
        assertThat(PhoneFormat.landline("0892123456")).contains("089-212-3456");
    }

    @Test
    @DisplayName("市話：不合法的輸入（太短、太長、不是區碼、含分機文字）一律不接受")
    void invalidLandline() {
        assertThat(PhoneFormat.landline("2234-5678")).isEmpty();
        assertThat(PhoneFormat.landline("0122345678")).isEmpty();
        assertThat(PhoneFormat.landline("0922345678")).isEmpty();
        assertThat(PhoneFormat.landline("02-2234-5678#12")).isEmpty();
        assertThat(PhoneFormat.landline("02223456789")).isEmpty();
        assertThat(PhoneFormat.landline("")).isEmpty();
    }
}
