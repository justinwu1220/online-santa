package com.onlinesanta.storage;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.SignUrlOption;

/**
 * Google Cloud Storage 的實作。
 *
 * <p><strong>在 Cloud Run 上簽章的注意事項：</strong>執行環境的服務帳號沒有私鑰檔，
 * 無法在本地做簽章運算，函式庫會改呼叫 IAM 的 {@code signBlob} API。這需要該服務帳號
 * 擁有<em>自己</em>的 {@code roles/iam.serviceAccountTokenCreator}——少了這個權限，
 * 程式在本機（有金鑰檔）跑得好好的，一上 Cloud Run 就會失敗。
 *
 * <p>也因為每次簽章都是一次網路往返，公開的禮物示意圖刻意放在公開 bucket 用固定
 * 網址讀取，不走簽章。
 */
@Component
@Profile("!dev-storage")
public class GcsObjectStorage implements ObjectStorage {

    private final Storage storage;
    private final StorageProperties properties;

    public GcsObjectStorage(Storage storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public UploadTarget createUploadUrl(StorageBucket bucket, String objectName,
                                        String contentType, Duration ttl) {
        BlobInfo blob = BlobInfo
                .newBuilder(BlobId.of(properties.bucketName(bucket), objectName))
                .setContentType(contentType)
                .build();

        URL url = storage.signUrl(blob, ttl.toSeconds(), TimeUnit.SECONDS,
                SignUrlOption.httpMethod(HttpMethod.PUT),
                // Content-Type 納入簽章：拿到網址的人不能改上傳別種型別的檔案
                SignUrlOption.withContentType(),
                SignUrlOption.withV4Signature());

        return new UploadTarget(url.toString(), contentType, Instant.now().plus(ttl));
    }

    @Override
    public Optional<StoredObject> find(StorageBucket bucket, String objectName) {
        Blob blob = storage.get(BlobId.of(properties.bucketName(bucket), objectName));
        return blob == null || !blob.exists()
                ? Optional.empty()
                : Optional.of(new StoredObject(objectName, blob.getContentType(), blob.getSize()));
    }

    @Override
    public String createDownloadUrl(StorageBucket bucket, String objectName, Duration ttl) {
        BlobInfo blob = BlobInfo
                .newBuilder(BlobId.of(properties.bucketName(bucket), objectName))
                .build();

        return storage.signUrl(blob, ttl.toSeconds(), TimeUnit.SECONDS,
                SignUrlOption.httpMethod(HttpMethod.GET),
                SignUrlOption.withV4Signature()).toString();
    }

    @Override
    public String publicUrl(String objectName) {
        return "https://storage.googleapis.com/%s/%s"
                .formatted(properties.publicBucket(), objectName);
    }

    @Override
    public void delete(StorageBucket bucket, String objectName) {
        storage.delete(BlobId.of(properties.bucketName(bucket), objectName));
    }
}
