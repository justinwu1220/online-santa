package com.onlinesanta.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("本機不得連到正式資料庫")
class LocalDatabaseGuardTest {

    private static void verify(String url) {
        new LocalDatabaseGuard(url).verify();
    }

    @Test
    @DisplayName("自己電腦上的資料庫，名稱不拘")
    void localhostIsAlwaysFine() {
        assertThatCode(() -> verify("jdbc:postgresql://localhost:5432/online_santa")).doesNotThrowAnyException();
        assertThatCode(() -> verify("jdbc:postgresql://127.0.0.1/anything")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("遠端資料庫名稱含 staging、dev、test、local 可以")
    void remoteNonProductionNamesAreFine() {
        String host = "jdbc:postgresql://ep-x.ap-southeast-1.aws.neon.tech/";
        assertThatCode(() -> verify(host + "online_santa_staging?sslmode=require")).doesNotThrowAnyException();
        assertThatCode(() -> verify(host + "online_santa_dev")).doesNotThrowAnyException();
        assertThatCode(() -> verify(host + "Online_Santa_TEST?sslmode=require")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("遠端資料庫名稱看不出不是正式庫就拒絕（例如 neondb）")
    void remoteProductionLookingNamesAreRefused() {
        assertThatThrownBy(() -> verify("jdbc:postgresql://ep-x.neon.tech/neondb?sslmode=require"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("neondb");
        assertThatThrownBy(() -> verify("jdbc:postgresql://ep-x-pooler.neon.tech/online_santa"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("看不懂的網址不擋")
    void unknownUrlsAreNotBlocked() {
        assertThatCode(() -> verify("jdbc:h2:mem:testdb")).doesNotThrowAnyException();
    }
}
