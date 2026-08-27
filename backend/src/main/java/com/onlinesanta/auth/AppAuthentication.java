package com.onlinesanta.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 通過驗證的請求身分。
 *
 * <p>權限來自本地 {@code users} 表而非 token 裡的 claims，因此 Admin 核准機構後，
 * 該機構成員的下一個請求就立刻生效，不必等 ID token 過期重簽。
 */
public class AppAuthentication extends AbstractAuthenticationToken {

    private final transient Jwt token;
    private final transient AppPrincipal principal;

    public AppAuthentication(Jwt token, AppPrincipal principal) {
        super(authoritiesOf(principal));
        this.token = token;
        this.principal = principal;
        setAuthenticated(true);
    }

    private static Collection<GrantedAuthority> authoritiesOf(AppPrincipal principal) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
    }

    @Override
    public AppPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return token;
    }

    @Override
    public String getName() {
        return principal.userId().toString();
    }
}
