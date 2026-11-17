package com.onlinesanta.config;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 本機（{@code local} profile）不得連到正式資料庫。
 *
 * <p>本機可以連自己電腦上的 PostgreSQL，也可以連遠端的測試站資料庫（例如 Neon 上的
 * {@code online_santa_staging}），但後者只靠資料庫名稱分辨：名稱必須含有 dev、staging、test 或 local
 * 其中之一，否則直接拒絕啟動。啟動時 Flyway 會對這個資料庫跑 migration，連到正式庫的話
 * 一個破壞性的 migration 就會讓線上的服務壞掉，而且無法回復——寧可在這裡擋下來。
 */
@Component
@Profile("local")
public class LocalDatabaseGuard {

    private static final Pattern DATABASE = Pattern.compile("^jdbc:postgresql://([^/:?]+)(?::\\d+)?/([^/?]+)");

    private final String url;

    public LocalDatabaseGuard(@Value("${spring.datasource.url}") String url) {
        this.url = url;
    }

    @PostConstruct
    void verify() {
        Matcher matcher = DATABASE.matcher(url);
        if (!matcher.find()) {
            return; // 不是看得懂的 PostgreSQL 網址，不擋
        }
        String host = matcher.group(1).toLowerCase(Locale.ROOT);
        String database = matcher.group(2).toLowerCase(Locale.ROOT);

        boolean isThisComputer = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
        boolean looksLikeNonProduction = database.contains("dev") || database.contains("staging")
                || database.contains("test") || database.contains("local");
        if (!isThisComputer && !looksLikeNonProduction) {
            throw new IllegalStateException(
                    "本機（local profile）拒絕連到遠端資料庫「%s」：".formatted(database)
                            + "名稱必須含有 dev、staging、test 或 local，才能確定它不是正式資料庫。"
                            + "檢查 DB_URL（環境變數或 backend/.env.local）。");
        }
    }
}
