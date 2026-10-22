package com.onlinesanta.config;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.common.exception.ForbiddenException;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.organization.OrganizationStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 機構被停權時，擋下它對願望的所有寫入動作（新增、修改、送審、恢復、刪除、收回認領、確認收到…）。
 *
 * <p>做在單一攔截器而不是逐一端點加檢查：日後新增端點不會漏擋。只擋兩組路徑的非讀取請求
 * ——{@code /api/organizations/me/**}（機構後台）與 {@code /api/wishes/**}（願望的寫入）；
 * 白名單是停權期間機構仍可做的事：修改機構資料、申請解除停權、與管理員的團隊對話。
 * 檔案上傳（{@code /api/uploads}）不在擋的路徑裡，由 {@code AttachmentService} 依用途判斷：
 * 機構文件與照片可以補，願望與收件證明的相關上傳不行。
 *
 * <p>傳訊給認領者的限制在 {@code ClaimRestriction}（機構停權時認領受限），不靠這裡。
 */
@Component
public class SuspendedOrganizationInterceptor implements HandlerInterceptor {

    private static final String[] GUARDED = {"/api/organizations/me/**", "/api/wishes/**"};
    private static final String[] ALLOWED = {
            "/api/organizations/me", "/api/organizations/me/unlock-request/**", "/api/organizations/me/team-chat/**"};

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final CurrentUserService currentUser;
    private final OrganizationRepository organizations;

    public SuspendedOrganizationInterceptor(CurrentUserService currentUser, OrganizationRepository organizations) {
        this.currentUser = currentUser;
        this.organizations = organizations;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isRead(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (matchesAny(GUARDED, path) && !matchesAny(ALLOWED, path)) {
            currentUser.find()
                    .filter(AppPrincipal::isOrgMember)
                    .map(AppPrincipal::organizationId)
                    .flatMap(organizations::findById)
                    .filter(organization -> organization.getStatus() == OrganizationStatus.SUSPENDED)
                    .ifPresent(organization -> {
                        throw new ForbiddenException("ORGANIZATION_SUSPENDED",
                                "貴機構目前被停權，無法對願望進行操作；請到「與團隊對話」與管理員聯繫");
                    });
        }
        return true;
    }

    private static boolean isRead(String method) {
        return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method);
    }

    private boolean matchesAny(String[] patterns, String path) {
        for (String pattern : patterns) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
