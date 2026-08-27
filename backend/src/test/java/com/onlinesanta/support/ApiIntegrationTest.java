package com.onlinesanta.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP 層整合測試的基底。
 *
 * <p>{@code @Transactional} 讓每個測試結束後自動回滾，測試之間不會互相汙染。
 */
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Transactional
public abstract class ApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /**
     * 以指定的 email 作為操作者發送請求。
     *
     * <p>帶的是真正簽章過的 ID token，會完整走過解碼、驗證與身分轉換的流程。
     */
    protected MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String email) {
        return builder.header("Authorization", "Bearer " + TestJwtSupport.tokenFor(email));
    }

    protected MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder,
                                                     Object body) {
        try {
            return builder.contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException("序列化測試請求內容失敗", e);
        }
    }
}
