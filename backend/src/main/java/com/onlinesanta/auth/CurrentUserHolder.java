package com.onlinesanta.auth;

/**
 * 以 ThreadLocal 保存本次請求的操作者。
 *
 * <p>M3 導入 Spring Security 後，這裡會改為從 {@code SecurityContextHolder} 取值，
 * 而 {@link CurrentUserService} 的介面維持不變——業務程式碼不必跟著改。
 */
final class CurrentUserHolder {

    private static final ThreadLocal<AppPrincipal> CURRENT = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    static void set(AppPrincipal principal) {
        CURRENT.set(principal);
    }

    static AppPrincipal get() {
        return CURRENT.get();
    }

    /** 必須在 finally 呼叫：容器會重用執行緒，殘留的身分會導致跨請求的權限外洩。 */
    static void clear() {
        CURRENT.remove();
    }
}
