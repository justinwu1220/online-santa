package com.onlinesanta.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 整合測試的共用基底：連上真實的 PostgreSQL 並讓 Flyway 跑完整套 migration。
 *
 * <p>認領的防超賣機制依賴 PostgreSQL 專屬行為（部分唯一索引、列鎖），用 H2 測不出來，
 * 因此整合測試一律跑在真的 PostgreSQL 上。資料庫有兩種來源：
 *
 * <ul>
 *   <li><strong>預設：Testcontainers</strong>——需要 Docker。容器以 static 宣告，讓所有子類共用同一個
 *       實例，避免每個測試類別重啟一次容器。</li>
 *   <li><strong>設定了 {@code TEST_DB_URL}：外部資料庫</strong>（例如另外建一個 Neon 測試資料庫），
 *       不需要 Docker。再用 {@code TEST_DB_USERNAME}、{@code TEST_DB_PASSWORD} 給帳密。
 *       這三個值可以放環境變數，或放在 {@code backend/.env.local}（每行 {@code KEY=VALUE}，
 *       已被 .gitignore 擋住，不會進 repo）。</li>
 * </ul>
 *
 * <p><strong>外部資料庫的防呆：資料庫名稱必須含有 {@code test}。</strong>這些測試會跑 migration、
 * 整張表地刪資料（見 {@code ClaimConcurrencyIT}），一旦不小心指到正式的資料庫就是災難。
 * 名稱不含 test 就直接拒絕執行，而不是靠「我應該不會設錯」。
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    private static final String EXTERNAL_URL = setting("TEST_DB_URL");

    /** 沒有指定外部資料庫時才會建立並啟動；有指定時是 null，也不會碰 Docker。 */
    static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_URL != null ? null :
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("online_santa")
                    .withUsername("santa")
                    .withPassword("santa")
                    .withReuse(true);

    static {
        if (POSTGRES != null) {
            POSTGRES.start();
        } else {
            requireTestDatabase(EXTERNAL_URL);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> setting("TEST_DB_USERNAME"));
            registry.add("spring.datasource.password", () -> setting("TEST_DB_PASSWORD"));
        }
    }

    /**
     * 環境變數優先，其次是同名的系統屬性（{@code -DTEST_DB_URL=...}），最後是 {@code .env.local}；
     * 空白視為沒設定。
     */
    private static String setting(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        if (value == null || value.isBlank()) {
            value = fromEnvFile(name);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 讀 {@code .env.local}（工作目錄是 backend/）。檔案不存在就當作沒有。 */
    private static String fromEnvFile(String name) {
        java.nio.file.Path file = java.nio.file.Path.of(".env.local");
        if (!java.nio.file.Files.isRegularFile(file)) {
            return null;
        }
        try {
            for (String line : java.nio.file.Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (trimmed.substring(0, eq).trim().equals(name)) {
                    String value = trimmed.substring(eq + 1).trim();
                    // 容許用引號包起來（密碼裡有特殊字元時）
                    if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                            || value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("讀不了 .env.local：" + e.getMessage(), e);
        }
        return null;
    }

    /** JDBC 網址最後一段（問號之前）是資料庫名稱，必須含有 test。 */
    static void requireTestDatabase(String jdbcUrl) {
        String withoutQuery = jdbcUrl.contains("?") ? jdbcUrl.substring(0, jdbcUrl.indexOf('?')) : jdbcUrl;
        String database = withoutQuery.substring(withoutQuery.lastIndexOf('/') + 1);
        if (!database.toLowerCase().contains("test")) {
            throw new IllegalStateException(
                    "拒絕在資料庫「%s」上跑整合測試：名稱必須含有 test。".formatted(database)
                            + "這些測試會整張表地刪資料，請另外建立專用的測試資料庫（例如 online_santa_test），"
                            + "再把 TEST_DB_URL 指向它。");
        }
    }
}
