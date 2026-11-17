package com.onlinesanta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                "claim_events", "attachments", "messages", "terms_consents");
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

    @Test
    void usersHaveOnboardedAtAndWishesHaveAvatar() {
        List<String> userColumns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'users'",
                String.class);
        assertThat(userColumns).contains("onboarded_at");

        List<String> wishColumns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'wishes'",
                String.class);
        assertThat(wishColumns).contains("avatar");

        String nullable = jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'wishes' AND column_name = 'category'", String.class);
        assertThat(nullable).as("1018 新版起禮物分類不再必填").isEqualTo("YES");
    }

    /**
     * 個資異動紀錄的價值全在「這些列沒被動過」，所以 append-only 要由資料庫保證，
     * 不能只靠 JPA 的 updatable=false（那只擋得住應用程式這條路）。
     */
    @Test
    void profileChangeLogsAreAppendOnlyAtTheDatabaseLevel() {
        jdbc.update("INSERT INTO profile_change_logs (target_type, target_id, changes, source) "
                + "VALUES ('USER', gen_random_uuid(), '[]', 'SYSTEM')");

        // 用 stack trace 比對：Spring 會把 plpgsql 的 RAISE 包成自己的例外，訊息在 cause 裡
        assertThatThrownBy(() -> jdbc.update("UPDATE profile_change_logs SET changes = '[\"tampered\"]'"))
                .hasStackTraceContaining("只能新增");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM profile_change_logs"))
                .hasStackTraceContaining("只能新增");
    }

    @Test
    void profileChangeLogsHaveTheIndexesEachQueryNeeds() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'profile_change_logs'",
                String.class);

        assertThat(indexes).contains(
                "idx_profile_change_logs_recent",
                "idx_profile_change_logs_target",
                "idx_profile_change_logs_type",
                "idx_profile_change_logs_actor");
    }
}
