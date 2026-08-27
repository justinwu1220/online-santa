package com.onlinesanta.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageBucket;
import com.onlinesanta.storage.StoredObject;
import com.onlinesanta.storage.UploadTarget;

/**
 * 測試用的儲存空間。
 *
 * <p>刻意<strong>不</strong>在發出上傳網址時就記錄物件存在——真實世界裡拿到網址不等於
 * 檔案已上傳。測試必須明確呼叫 {@link #simulateUpload} 才算傳好，這樣「宣稱上傳完成
 * 但檔案其實不存在」的情境才測得到。
 */
public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

    @Override
    public UploadTarget createUploadUrl(StorageBucket bucket, String objectName,
                                        String contentType, Duration ttl) {
        return new UploadTarget(
                "https://storage.test/%s/%s?upload".formatted(bucket.name().toLowerCase(), objectName),
                contentType,
                Instant.now().plus(ttl));
    }

    @Override
    public Optional<StoredObject> find(StorageBucket bucket, String objectName) {
        return Optional.ofNullable(objects.get(key(bucket, objectName)));
    }

    @Override
    public String createDownloadUrl(StorageBucket bucket, String objectName, Duration ttl) {
        return "https://storage.test/%s/%s?signature=test&expires=%d".formatted(
                bucket.name().toLowerCase(), objectName, Instant.now().plus(ttl).getEpochSecond());
    }

    @Override
    public String publicUrl(String objectName) {
        return "https://storage.test/public/" + objectName;
    }

    @Override
    public void delete(StorageBucket bucket, String objectName) {
        objects.remove(key(bucket, objectName));
    }

    // ------------------------------------------------------------ 測試控制

    /** 模擬前端把檔案 PUT 上去了。 */
    public void simulateUpload(StorageBucket bucket, String objectName,
                               String contentType, long sizeBytes) {
        objects.put(key(bucket, objectName), new StoredObject(objectName, contentType, sizeBytes));
    }

    public boolean exists(StorageBucket bucket, String objectName) {
        return objects.containsKey(key(bucket, objectName));
    }

    public int size() {
        return objects.size();
    }

    public void reset() {
        objects.clear();
    }

    private String key(StorageBucket bucket, String objectName) {
        return bucket.name() + ":" + objectName;
    }
}
