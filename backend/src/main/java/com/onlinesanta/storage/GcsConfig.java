package com.onlinesanta.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

/**
 * GCS 用戶端。憑證由 Application Default Credentials 取得：Cloud Run 上是執行環境
 * 的服務帳號，本機則是 {@code GOOGLE_APPLICATION_CREDENTIALS} 指向的金鑰檔。
 *
 * <p>刻意延遲建立（見注入點的 {@code @Lazy}）：建立用戶端要做憑證探索，在 Cloud Run
 * 上還會打一次 metadata server。這件事只有真的要簽 URL 時才需要，不該拖慢冷啟動——
 * 而多數請求（瀏覽願望牆）根本不碰儲存。
 *
 * <p>副作用是建置映像檔時的 CDS training run 不需要任何 GCP 憑證。
 */
@Configuration
@Profile("!dev-storage")
public class GcsConfig {

    @Bean
    @Lazy
    Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}
