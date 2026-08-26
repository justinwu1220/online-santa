package com.onlinesanta.auth;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 開發與測試用的身分模擬——依 {@code X-Dev-User-Email} 標頭認人，沒有任何驗證。
 *
 * <p><strong>絕不能在正式環境啟用</strong>，因此以 {@code @Profile("!prod")} 排除，
 * 並在建立時輸出警告。M3 會由驗證 Firebase ID token 的 filter 取代它；屆時只需
 * 刪掉這個類別，{@link CurrentUserService} 的呼叫端完全不受影響。
 */
@Component
@Profile("!prod")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DevPrincipalFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DevPrincipalFilter.class);

    public static final String EMAIL_HEADER = "X-Dev-User-Email";
    public static final String NAME_HEADER = "X-Dev-User-Name";

    private final DevUserProvisioner provisioner;

    public DevPrincipalFilter(DevUserProvisioner provisioner) {
        this.provisioner = provisioner;
        log.warn("DevPrincipalFilter 已啟用：任何請求都能以 {} 標頭指定身分。"
                + "這只應出現在開發與測試環境。", EMAIL_HEADER);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String email = request.getHeader(EMAIL_HEADER);
        try {
            if (StringUtils.hasText(email)) {
                CurrentUserHolder.set(
                        provisioner.resolve(email.trim(), request.getHeader(NAME_HEADER)));
            }
            chain.doFilter(request, response);
        } finally {
            // 容器會重用執行緒，未清除的身分會外洩到下一個請求
            CurrentUserHolder.clear();
        }
    }
}
