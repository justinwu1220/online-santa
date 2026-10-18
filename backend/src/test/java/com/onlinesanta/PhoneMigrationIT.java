package com.onlinesanta;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.onlinesanta.support.PostgresIntegrationTest;

import db.migration.V25__normalize_organization_phones;

/** 既有機構的電話在 V25 改存加 dash 的格式；看不懂的舊資料原樣保留。 */
class PhoneMigrationIT extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    private final List<UUID> inserted = new java.util.ArrayList<>();

    /** 資料庫是各測試共用的：自己插的機構要清掉，免得影響其他測試對機構清單的斷言。 */
    @AfterEach
    void cleanUp() {
        inserted.forEach(id -> jdbc.update("DELETE FROM organizations WHERE id = ?", id));
    }

    @Test
    void normalizesValidPhonesAndLeavesUnrecognizedOnesAlone() throws Exception {
        UUID clean = insertOrganization("整理之家", "0422345678", "0912345678");
        UUID odd = insertOrganization("有分機之家", "04-2234-5678#12", "請洽櫃檯");
        UUID blank = insertOrganization("沒市話之家", null, "0987654321");

        try (Connection connection = dataSource.getConnection()) {
            new V25__normalize_organization_phones().migrate(new Context() {
                @Override
                public Configuration getConfiguration() {
                    return null;
                }

                @Override
                public Connection getConnection() {
                    return connection;
                }
            });
        }

        assertThat(phones(clean)).containsExactly("04-2234-5678", "0912-345-678");
        assertThat(phones(odd)).containsExactly("04-2234-5678#12", "請洽櫃檯");
        assertThat(phones(blank)).containsExactly(null, "0987-654-321");
    }

    private UUID insertOrganization(String name, String contactPhone, String mobilePhone) {
        UUID id = UUID.randomUUID();
        inserted.add(id);
        jdbc.update("INSERT INTO organizations (id, name, contact_email, contact_phone, mobile_phone, status) "
                        + "VALUES (?, ?, 'a@example.org', ?, ?, 'APPROVED')",
                id, name + id, contactPhone, mobilePhone);
        return id;
    }

    private List<String> phones(UUID id) {
        return jdbc.query("SELECT contact_phone, mobile_phone FROM organizations WHERE id = ?",
                (rs, n) -> Arrays.asList(rs.getString(1), rs.getString(2)), id).get(0);
    }
}
