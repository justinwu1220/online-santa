package com.onlinesanta.config;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.ClaimRestriction;
import com.onlinesanta.common.exception.ForbiddenException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 認領者被停權（受限登入）時，擋下所有寫入動作：認領、傳訊、回報寄送、取消、改個人資料…。
 *
 * <p>做在單一攔截器而不是逐一端點加檢查：日後新增端點不會漏擋。讀取不擋——被停權的人要能登入、
 * 看到停權通知與自己的認領狀態。沒有白名單：連「同意須知」「看完首次說明」這類寫入也不開放。
 * 停權的是一般認領者（DONOR）；機構成員與管理員不會進入這個狀態。
 */
@Component
public class SuspendedUserInterceptor implements HandlerInterceptor {

    private final CurrentUserService currentUser;

    public SuspendedUserInterceptor(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method)) {
            return true;
        }
        currentUser.find().filter(AppPrincipal::suspended).ifPresent(principal -> {
            throw new ForbiddenException("ACCOUNT_SUSPENDED",
                    "你的帳號目前受到限制，無法進行這個操作。若有問題可聯絡：" + ClaimRestriction.CONTACT_EMAIL);
        });
        return true;
    }
}
