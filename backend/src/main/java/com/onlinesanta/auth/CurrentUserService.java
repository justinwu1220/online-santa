package com.onlinesanta.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.onlinesanta.common.exception.ForbiddenException;
import com.onlinesanta.common.exception.UnauthenticatedException;

/**
 * 取得目前操作者的唯一入口。業務程式碼只依賴這個介面，不直接碰身分驗證的實作細節。
 */
@Service
public class CurrentUserService {

    public Optional<AppPrincipal> find() {
        return Optional.ofNullable(CurrentUserHolder.get());
    }

    public AppPrincipal require() {
        return find().orElseThrow(UnauthenticatedException::new);
    }

    /** 取得目前操作者，並確認其為機構成員；回傳所屬機構 id。 */
    public UUID requireOrganizationId() {
        AppPrincipal principal = require();
        if (!principal.isOrgMember() || principal.organizationId() == null) {
            throw new ForbiddenException("NOT_ORG_MEMBER", "此操作僅限機構成員");
        }
        return principal.organizationId();
    }

    public AppPrincipal requireAdmin() {
        AppPrincipal principal = require();
        if (!principal.isAdmin()) {
            throw new ForbiddenException("NOT_ADMIN", "此操作僅限平台管理員");
        }
        return principal;
    }
}
