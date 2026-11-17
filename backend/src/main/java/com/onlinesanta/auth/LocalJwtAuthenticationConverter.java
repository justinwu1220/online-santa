package com.onlinesanta.auth;

import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 把驗證通過的 access token 轉換成本系統的身分。
 *
 * <p>token 只用來確認「這是誰」；「這個人能做什麼」一律回資料庫查
 * （見 {@link UserAuthenticationService}）。若把角色寫進 token，管理員核准機構後該機構成員
 * 得等 token 過期重簽，權限才會生效，體驗上等同於卡住。
 */
@Component
public class LocalJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserAuthenticationService authentication;

    public LocalJwtAuthenticationConverter(UserAuthenticationService authentication) {
        this.authentication = authentication;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IdentityRejectedException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "請先登入");
        }
        return new AppAuthentication(jwt, authentication.principalFor(userId));
    }
}
