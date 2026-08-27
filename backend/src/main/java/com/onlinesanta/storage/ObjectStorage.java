package com.onlinesanta.storage;

import java.time.Duration;
import java.util.Optional;

/**
 * 物件儲存的抽象。
 *
 * <p>存在的理由不只是「方便測試」：本機開發也用得到。有了它，沒有 GCP 專案的人
 * 一樣能把整個上傳流程跑起來（見 {@link LocalObjectStorage}），M6 開發前端上傳
 * 元件時不必先申請雲端資源。
 */
public interface ObjectStorage {

    /**
     * 產生一個限時的上傳網址，讓前端直接把檔案 PUT 上去。
     *
     * <p>檔案不經過本服務——省下 Cloud Run 的運算時間，也不受請求逾時限制。
     *
     * @param contentType 會被綁進簽章。前端若改送別的型別，儲存端會直接拒絕，
     *                    避免有人拿到上傳網址後改上傳可執行檔
     */
    UploadTarget createUploadUrl(StorageBucket bucket, String objectName,
                                 String contentType, Duration ttl);

    /**
     * 讀取物件的中繼資料。
     *
     * <p>用於上傳完成後的確認：前端宣稱上傳成功不算數，要由後端向儲存端查證檔案
     * 確實存在、型別與大小也符合。
     */
    Optional<StoredObject> find(StorageBucket bucket, String objectName);

    /** 產生限時的讀取網址。只用於 {@link StorageBucket#PRIVATE}。 */
    String createDownloadUrl(StorageBucket bucket, String objectName, Duration ttl);

    /** 公開 bucket 的固定網址，不需簽章。 */
    String publicUrl(String objectName);

    void delete(StorageBucket bucket, String objectName);
}
