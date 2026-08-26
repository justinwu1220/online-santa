package com.onlinesanta.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 整合測試的共用基底：啟動真實的 PostgreSQL 並讓 Flyway 跑完整套 migration。
 *
 * <p>認領的防超賣機制依賴 PostgreSQL 專屬行為（部分唯一索引、列鎖），用 H2 測不出來，
 * 因此整合測試一律跑在真的 PostgreSQL 上。容器以 static 宣告，讓所有子類共用同一個
 * 實例，避免每個測試類別重啟一次容器。
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("online_santa")
                    .withUsername("santa")
                    .withPassword("santa")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
