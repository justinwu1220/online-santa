package com.onlinesanta.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 把驗證通過的 Firebase ID token 轉換成本系統的身分。
 *
 * <p>token 只用來確認「這是誰」；「這個人能做什麼」一律回資料庫查。
 * 若改用 Firebase custom claims 帶角色，Admin 核准機構後該機構成員得等到
 * ID token 過期重簽（最長一小時）權限才會生效，體驗上等同於卡住。
 *
 * <p>{@code email_verified} 必須一併帶下去：開放密碼註冊之後，Firebase 不會驗證
 * 註冊者是否真的擁有那個信箱，而系統有兩處原本依賴「email 一定驗證過」
 * （管理員白名單、以 email 接管既有帳號）。見 {@link UserProvisioningService}。
 */
@Component
public class FirebaseAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserProvisioningService provisioning;

    public FirebaseAuthenticationConverter(UserProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String firebaseUid = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String displayName = jwt.getClaimAsString("name");
        // claim 不存在時視為未驗證——寧可誤判成未驗證，也不要誤判成已驗證
        boolean emailVerified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));

        return new AppAuthentication(jwt,
                provisioning.resolve(firebaseUid, email, emailVerified, displayName));
    }
}
