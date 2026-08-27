package com.onlinesanta.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

/**
 * GCS 用戶端。憑證由 Application Default Credentials 取得：Cloud Run 上是執行環境
 * 的服務帳號，本機則是 {@code GOOGLE_APPLICATION_CREDENTIALS} 指向的金鑰檔。
 */
@Configuration
@Profile("!dev-storage")
public class GcsConfig {

    @Bean
    Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}
