package com.onlinesanta.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * 本機開發用的假 Cloud Storage 端點。
 *
 * <p>接收 {@link LocalObjectStorage} 簽出的網址，讓前端的「直傳」流程在本機也能
 * 真的跑起來。簽章與有效期都會驗——不是無條件接收，這樣本機才測得到「網址過期」
 * 這類情境。
 *
 * <p>只在 {@code dev-storage} profile 存在，正式環境連這個類別都不會被載入。
 */
@RestController
@RequestMapping("/dev-storage")
@Profile("dev-storage")
@Hidden
public class DevStorageController {

    private static final Logger log = LoggerFactory.getLogger(DevStorageController.class);

    private final LocalObjectStorage storage;

    public DevStorageController(LocalObjectStorage storage) {
        this.storage = storage;
        log.warn("dev-storage profile 已啟用：檔案存在本機磁碟 {}，不會上傳到 Cloud Storage。",
                storage.properties().localStorageDir());
    }

    @PutMapping("/{bucket}/**")
    public ResponseEntity<Void> upload(@PathVariable String bucket,
                                       @RequestParam long expires,
                                       @RequestParam String signature,
                                       @RequestHeader("Content-Type") String contentType,
                                       @RequestBody byte[] content,
                                       jakarta.servlet.http.HttpServletRequest request) {
        StorageBucket target = parseBucket(bucket);
        String objectName = objectNameOf(request, bucket);

        if (!storage.verify("PUT", target, objectName, contentType, expires, signature)) {
            // 對應 GCS 在簽章不符或過期時的行為
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        storage.write(target, objectName, contentType, content);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{bucket}/**")
    public ResponseEntity<Resource> download(@PathVariable String bucket,
                                             @RequestParam(required = false) Long expires,
                                             @RequestParam(required = false) String signature,
                                             jakarta.servlet.http.HttpServletRequest request)
            throws IOException {
        StorageBucket target = parseBucket(bucket);
        String objectName = objectNameOf(request, bucket);

        // 公開 bucket 不需簽章，私密 bucket 一定要——與正式環境的權限模型一致
        if (target == StorageBucket.PRIVATE
                && (expires == null
                || !storage.verify("GET", target, objectName, "", expires, signature))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var file = storage.pathOf(target, objectName);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = storage.find(target, objectName)
                .map(StoredObject::contentType)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(file));
    }

    private StorageBucket parseBucket(String bucket) {
        return StorageBucket.valueOf(bucket.toUpperCase(Locale.ROOT));
    }

    /** 取出 {@code /dev-storage/{bucket}/} 之後的完整路徑。 */
    private String objectNameOf(jakarta.servlet.http.HttpServletRequest request, String bucket) {
        String prefix = "/dev-storage/" + bucket + "/";
        String uri = java.net.URLDecoder.decode(
                request.getRequestURI(), java.nio.charset.StandardCharsets.UTF_8);
        return uri.substring(uri.indexOf(prefix) + prefix.length());
    }
}
