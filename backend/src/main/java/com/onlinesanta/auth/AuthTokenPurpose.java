package com.onlinesanta.auth;

/** 一次性 token 的用途。 */
public enum AuthTokenPurpose {
    /** 信箱驗證碼（6 位數）。 */
    VERIFY_EMAIL,
    /** 重設密碼連結裡的 token。 */
    RESET_PASSWORD
}
