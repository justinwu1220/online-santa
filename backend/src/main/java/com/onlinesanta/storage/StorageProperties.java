package com.onlinesanta.storage;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 圖片儲存的設定（{@code app.storage.*}）。
 *
 * @param publicBucket        公開 bucket 名稱（禮物示意圖）
 * @param wishImageEnabled    是否開放上傳願望示意圖；關閉時公開 bucket 不需要存在
 * @param privateBucket       私密 bucket 名稱（寄送證明、機構回饋照片）
 * @param uploadUrlTtl        上傳網址的有效期
 * @param downloadUrlTtl      讀取網址的有效期
 * @param maxUploadBytes      單檔大小上限
 * @param allowedContentTypes 允許的 MIME 型別
 * @param localStorageDir     本機開發時檔案落地的目錄（僅 dev-storage profile 使用）
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String publicBucket,
        boolean wishImageEnabled,
        String privateBucket,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl,
        long maxUploadBytes,
        List<String> allowedContentTypes,
        String localStorageDir) {

    /**
     * MIME 型別對應的副檔名。只收這幾種——SVG 可以夾帶腳本，刻意不支援。
     *
     * <p>這張表只決定副檔名，不是白名單本身——{@code application/pdf} 能不能真的
     * 上傳，仍然由 {@code AttachmentService.requireAllowedContentType} 依用途判斷
     * （目前只有 {@code ORG_DOCUMENT} 開放 PDF）。加在這裡只是讓機構文件存成
     * {@code .pdf} 而不是退回預設的 {@code .bin}。
     */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf");

    public String bucketName(StorageBucket bucket) {
        return bucket == StorageBucket.PUBLIC ? publicBucket : privateBucket;
    }

    public boolean allows(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType.toLowerCase());
    }

    public String extensionFor(String contentType) {
        return EXTENSIONS.getOrDefault(contentType.toLowerCase(), "bin");
    }
}
