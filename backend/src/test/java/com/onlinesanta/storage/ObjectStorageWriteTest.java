package com.onlinesanta.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.onlinesanta.support.InMemoryObjectStorage;

/** 後端自己寫入物件的三個方法：put、openWrite、read（本機實作與測試替身行為一致）。 */
class ObjectStorageWriteTest {

    private static StorageProperties propertiesIn(Path dir) {
        return new StorageProperties("test-public", false, "test-private",
                Duration.ofMinutes(15), Duration.ofMinutes(10), 5_242_880L, List.of("image/jpeg"), dir.toString());
    }

    private static void check(ObjectStorage storage) throws Exception {
        assertThat(storage.read(StorageBucket.PRIVATE, "closing/none.pdf")).isEmpty();

        byte[] pdf = "%PDF-1.7 小檔".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        storage.put(StorageBucket.PRIVATE, "closing/receipts/OS2026-000001/red.pdf", "application/pdf", pdf);
        assertThat(storage.read(StorageBucket.PRIVATE, "closing/receipts/OS2026-000001/red.pdf")).contains(pdf);
        assertThat(storage.find(StorageBucket.PRIVATE, "closing/receipts/OS2026-000001/red.pdf"))
                .hasValueSatisfying(o -> {
                    assertThat(o.contentType()).isEqualTo("application/pdf");
                    assertThat(o.sizeBytes()).isEqualTo(pdf.length);
                });

        // 覆蓋寫入
        storage.put(StorageBucket.PRIVATE, "closing/receipts/OS2026-000001/red.pdf", "application/pdf", new byte[] {1});
        assertThat(storage.read(StorageBucket.PRIVATE, "closing/receipts/OS2026-000001/red.pdf")).contains(new byte[] {1});

        // 串流寫入：close 之後物件才存在
        try (OutputStream out = storage.openWrite(StorageBucket.PRIVATE, "closing/exports/x.zip", "application/zip")) {
            out.write(new byte[] {10, 20, 30});
        }
        assertThat(storage.read(StorageBucket.PRIVATE, "closing/exports/x.zip")).contains(new byte[] {10, 20, 30});

        storage.delete(StorageBucket.PRIVATE, "closing/exports/x.zip");
        assertThat(storage.read(StorageBucket.PRIVATE, "closing/exports/x.zip")).isEmpty();
    }

    @Test
    @DisplayName("本機儲存：寫入、覆蓋、串流寫入、讀回、刪除")
    void localStorage(@TempDir Path dir) throws Exception {
        check(new LocalObjectStorage(propertiesIn(dir)));
    }

    @Test
    @DisplayName("測試替身：行為與本機儲存一致")
    void inMemoryStorage() throws Exception {
        check(new InMemoryObjectStorage());
    }

    @Test
    @DisplayName("本機儲存擋掉路徑穿越")
    void localStorageRejectsTraversal(@TempDir Path dir) {
        LocalObjectStorage storage = new LocalObjectStorage(propertiesIn(dir));
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> storage.put(StorageBucket.PRIVATE, "../escape.pdf", "application/pdf", new byte[1]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
