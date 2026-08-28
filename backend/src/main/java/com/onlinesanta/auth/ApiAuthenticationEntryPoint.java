package com.onlinesanta.auth;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 未提供 token、token 無效／過期，或身分本身不被接受。 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter writer;

    public ApiAuthenticationEntryPoint(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        // 身分被明確拒絕時，把原因傳達出去——使用者才知道下一步該做什麼
        // （去收驗證信、或聯繫平台）。籠統的「請先登入」在這裡毫無幫助。
        if (exception instanceof IdentityRejectedException rejected) {
            writer.write(response, rejected.getStatus(),
                    rejected.getErrorCode(), rejected.getMessage());
            return;
        }

        writer.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "請先登入");
    }
}
