package com.onlinesanta.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 把檔案存在本機磁碟的實作，讓沒有 GCP 專案的人也能跑完整個上傳流程。
 *
 * <p>刻意模仿 GCS 的行為而非隨便回一個網址：網址一樣帶簽章、一樣會過期、
 * Content-Type 一樣綁進簽章。這樣本機測出來的行為才具參考價值——否則會出現
 * 「本機好好的、上雲就壞」的落差。
 *
 * <p>只在 {@code dev-storage} profile 建立，正式環境不存在這個 bean。
 */
@Component
@Profile("dev-storage")
public class LocalObjectStorage implements ObjectStorage {

    /** 每次啟動重新產生：重啟後舊網址一律失效，不留可長期使用的憑證。 */
    private static final byte[] SIGNING_KEY = new byte[32];

    static {
        new SecureRandom().nextBytes(SIGNING_KEY);
    }

    private final StorageProperties properties;
    private final Path root;

    public LocalObjectStorage(StorageProperties properties) {
        this.properties = properties;
        // root 也要 normalize 並轉絕對路徑，否則下面的 startsWith 檢查會誤判：
        // Path.of("./x") 與它 normalize 後的 "x" 並不 startsWith 彼此
        this.root = Path.of(properties.localStorageDir()).toAbsolutePath().normalize();
    }

    @Override
    public UploadTarget createUploadUrl(StorageBucket bucket, String objectName,
                                        String contentType, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        return new UploadTarget(
                signedUrl("PUT", bucket, objectName, contentType, expiresAt),
                contentType,
                expiresAt);
    }

    @Override
    public Optional<StoredObject> find(StorageBucket bucket, String objectName) {
        Path file = pathOf(bucket, objectName);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            Path meta = file.resolveSibling(file.getFileName() + ".type");
            String contentType = Files.isRegularFile(meta)
                    ? Files.readString(meta, StandardCharsets.UTF_8)
                    : "application/octet-stream";
            return Optional.of(new StoredObject(objectName, contentType, Files.size(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("讀取本機儲存的物件失敗：" + objectName, e);
        }
    }

    @Override
    public String createDownloadUrl(StorageBucket bucket, String objectName, Duration ttl) {
        return signedUrl("GET", bucket, objectName, "", Instant.now().plus(ttl));
    }

    @Override
    public String publicUrl(String objectName) {
        return "http://localhost:8080/dev-storage/public/" + encodePath(objectName);
    }

    @Override
    public void delete(StorageBucket bucket, String objectName) {
        Path file = pathOf(bucket, objectName);
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".type"));
        } catch (IOException e) {
            throw new UncheckedIOException("刪除本機儲存的物件失敗：" + objectName, e);
        }
    }

    // ------------------------------------------------------------ 內部

    Path pathOf(StorageBucket bucket, String objectName) {
        Path resolved = root.resolve(bucket.name().toLowerCase()).resolve(objectName).normalize();
        // 擋掉 ../ 之類的路徑穿越
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("物件名稱不合法：" + objectName);
        }
        return resolved;
    }

    void write(StorageBucket bucket, String objectName, String contentType, byte[] content) {
        Path file = pathOf(bucket, objectName);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content);
            Files.writeString(file.resolveSibling(file.getFileName() + ".type"), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("寫入本機儲存失敗：" + objectName, e);
        }
    }

    private String signedUrl(String method, StorageBucket bucket, String objectName,
                             String contentType, Instant expiresAt) {
        long expiry = expiresAt.getEpochSecond();
        String signature = sign(method, bucket, objectName, contentType, expiry);
        return "http://localhost:8080/dev-storage/%s/%s?expires=%d&signature=%s".formatted(
                bucket.name().toLowerCase(), encodePath(objectName), expiry, signature);
    }

    String sign(String method, StorageBucket bucket, String objectName,
                String contentType, long expiry) {
        String payload = String.join("\n", method, bucket.name(), objectName,
                contentType == null ? "" : contentType, Long.toString(expiry));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGNING_KEY, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("簽章失敗", e);
        }
    }

    /** 比對簽章用固定時間比較，避免時序側channel——正式環境的簽章驗證由 GCS 負責。 */
    boolean verify(String method, StorageBucket bucket, String objectName,
                   String contentType, long expiry, String signature) {
        if (Instant.now().getEpochSecond() > expiry) {
            return false;
        }
        byte[] expected = sign(method, bucket, objectName, contentType, expiry)
                .getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature == null
                ? new byte[0]
                : signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    StorageProperties properties() {
        return properties;
    }

    private static String encodePath(String objectName) {
        // 逐段編碼，保留斜線讓路徑結構仍然看得出來
        StringBuilder encoded = new StringBuilder();
        for (String segment : objectName.split("/")) {
            if (!encoded.isEmpty()) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(segment, StandardCharsets.UTF_8));
        }
        return encoded.toString();
    }
}
