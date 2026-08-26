package com.onlinesanta;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.onlinesanta.support.PostgresIntegrationTest;

/**
 * 驗證 Flyway migration 能在乾淨的 PostgreSQL 上跑完，且關鍵的資料完整性保護到位。
 */
class SchemaMigrationIT extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsAllApplicationTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);

        assertThat(tables).contains(
                "organizations", "users", "wishes", "claims",
                "claim_events", "attachments", "messages");
    }

    @Test
    void createsPartialUniqueIndexThatPreventsDoubleClaiming() {
        String definition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes "
                        + "WHERE tablename = 'claims' AND indexname = 'uq_active_claim_per_wish'",
                String.class);

        assertThat(definition)
                .as("同一願望不得同時存在兩筆有效認領——這是防超賣的資料庫層防線")
                .contains("UNIQUE")
                .contains("wish_id")
                .contains("WHERE");
    }

    @Test
    void wishesTableHasNoChildIdentifyingColumns() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'wishes'",
                String.class);

        assertThat(columns)
                .as("schema 層級不得出現孩童可識別欄位")
                .doesNotContain("child_name", "full_name", "birth_date", "birthday",
                        "child_photo", "photo_url", "national_id", "school_name");
        assertThat(columns).contains("child_alias", "age_range");
    }
}
