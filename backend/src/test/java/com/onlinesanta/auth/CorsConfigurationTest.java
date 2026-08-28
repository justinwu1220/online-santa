package com.onlinesanta.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 跨來源規則。
 *
 * <p>這幾個測試存在的理由：CORS 只有瀏覽器會執行，curl 與 MockMvc 都測不出漏洞。
 * 曾經因為只註冊了 {@code /api/**}，本機的圖片直傳在預檢就被回 403 Invalid CORS
 * request——後端每一支端點單獨看都是好的，只有真的開瀏覽器才會發現。
 */
@DisplayName("跨來源設定")
class CorsConfigurationTest {

    private static final String ORIGIN = "http://localhost:5173";

    private static CorsConfigurationSource sourceWith(String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        AuthProperties properties = new AuthProperties(
                "demo-project", List.of(), List.of(ORIGIN));
        return new SecurityConfig(properties, environment).corsConfigurationSource();
    }

    /** 模擬瀏覽器的預檢請求，取出這個路徑實際套用到的設定。 */
    private static CorsConfiguration preflight(CorsConfigurationSource source,
                                               String path, String method) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader("Origin", ORIGIN);
        request.addHeader("Access-Control-Request-Method", method);
        return source.getCorsConfiguration(request);
    }

    @Nested
    @DisplayName("API")
    class Api {

        @Test
        @DisplayName("前端網域可以呼叫 API")
        void theFrontendOriginMayCallTheApi() {
            CorsConfiguration config = preflight(sourceWith(), "/api/wishes", "POST");

            assertThat(config).isNotNull();
            assertThat(config.checkOrigin(ORIGIN)).isEqualTo(ORIGIN);
            assertThat(config.checkHttpMethod(org.springframework.http.HttpMethod.POST))
                    .isNotNull();
            assertThat(config.checkHeaders(List.of("authorization", "content-type")))
                    .isNotNull();
        }

        @Test
        @DisplayName("其他網域不行")
        void otherOriginsAreRejected() {
            CorsConfiguration config = preflight(sourceWith(), "/api/wishes", "POST");

            assertThat(config.checkOrigin("https://evil.example.com")).isNull();
        }
    }

    @Nested
    @DisplayName("本機的假 Cloud Storage")
    class DevStorage {

        @Test
        @DisplayName("啟用 dev-storage 時，直傳的 PUT 預檢會過")
        void thePreflightForADirectUploadSucceeds() {
            CorsConfiguration config = preflight(
                    sourceWith("dev-storage"), "/dev-storage/public/wishes/a/b.png", "PUT");

            assertThat(config)
                    .as("沒有這份設定，瀏覽器的預檢會拿到 403 Invalid CORS request")
                    .isNotNull();
            assertThat(config.checkOrigin(ORIGIN)).isEqualTo(ORIGIN);
            assertThat(config.checkHttpMethod(org.springframework.http.HttpMethod.PUT))
                    .as("直傳用的就是 PUT")
                    .isNotNull();
            assertThat(config.checkHeaders(List.of("content-type")))
                    .as("Content-Type 綁在簽章裡，一定會被送出")
                    .isNotNull();
        }

        @Test
        @DisplayName("讀取用的 GET 預檢也會過")
        void thePreflightForADownloadSucceeds() {
            CorsConfiguration config = preflight(
                    sourceWith("dev-storage"), "/dev-storage/private/proofs/a.png", "GET");

            assertThat(config.checkHttpMethod(org.springframework.http.HttpMethod.GET))
                    .isNotNull();
        }

        @Test
        @DisplayName("不帶 Authorization——授權來自網址上的簽章")
        void theTokenIsNotSentToStorage() {
            CorsConfiguration config = preflight(
                    sourceWith("dev-storage"), "/dev-storage/public/a.png", "PUT");

            // 正式環境的 GCS 也不吃我們的 token，本機放行只會讓兩邊行為分歧
            assertThat(config.checkHeaders(List.of("authorization"))).isNull();
        }

        @Test
        @DisplayName("沒有 dev-storage profile 時完全不存在")
        void itDoesNotExistInProduction() {
            CorsConfiguration config = preflight(
                    sourceWith(), "/dev-storage/public/a.png", "PUT");

            assertThat(config)
                    .as("正式環境沒有這個端點，檔案直傳到 GCS，CORS 由 bucket 自己設")
                    .isNull();
        }
    }
}
