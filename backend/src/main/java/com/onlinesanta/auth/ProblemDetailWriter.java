package com.onlinesanta.auth;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security 在進到 controller 之前就攔下請求，因此那些回應不會經過
 * {@code GlobalExceptionHandler}。這個類別讓它們維持相同的 RFC 7807 格式與
 * errorCode，前端不必為驗證錯誤另寫一套處理。
 */
@Component
public class ProblemDetailWriter {

    private final ObjectMapper json;

    public ProblemDetailWriter(ObjectMapper json) {
        this.json = json;
    }

    public void write(HttpServletResponse response, HttpStatus status,
                      String errorCode, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("errorCode", errorCode);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), problem);
    }
}
