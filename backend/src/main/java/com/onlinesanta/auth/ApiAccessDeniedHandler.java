package com.onlinesanta.auth;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 身分已確認，但權限不足。 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter writer;

    public ApiAccessDeniedHandler(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        writer.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "沒有執行此操作的權限");
    }
}
