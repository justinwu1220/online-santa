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

    /**
     * 同上，但要求瀏覽器以指定檔名下載（Content-Disposition: attachment）。
     * 物件路徑是 closing/receipts/OS2026-000001/white.pdf 這種，沒有指定的話下載下來會是 white.pdf，分不出是哪張。
     * 預設實作忽略檔名；GCS 實作會帶進簽章。檔名只能是 ASCII（不含引號與換行）。
     */
    default String createDownloadUrl(StorageBucket bucket, String objectName, Duration ttl, String downloadFileName) {
        return createDownloadUrl(bucket, objectName, ttl);
    }

    /** 公開 bucket 的固定網址，不需簽章。 */
    String publicUrl(String objectName);

    void delete(StorageBucket bucket, String objectName);

    /**
     * 設定物件的 {@code Cache-Control} 中繼資料。在 {@link StorageBucket#PUBLIC}
     * 確認上傳完成時呼叫——物件名稱每次上傳都帶一個新的 UUID（見
     * {@code AttachmentService#createUploadUrl}），同一個名稱不會被覆寫成別的內容，
     * 設成永久快取沒有「快取到舊圖」的風險。
     */
    void applyCacheControl(StorageBucket bucket, String objectName, String cacheControl);

    /**
     * 後端直接寫入一個物件（結案文件、收據 PDF 等後端自己產生的檔案）。已存在就覆蓋。
     */
    void put(StorageBucket bucket, String objectName, String contentType, byte[] content);

    /**
     * 串流寫入大型物件（匯出壓縮檔）：呼叫端寫完必須 {@code close()}，物件才算建立完成。
     * 不要整包放進記憶體：Cloud Run 只有 1Gi，而且同時有很多請求。
     */
    java.io.OutputStream openWrite(StorageBucket bucket, String objectName, String contentType);

    /** 讀回物件完整內容；不存在回 empty。只用於小檔（單張收據 PDF 等）。 */
    Optional<byte[]> read(StorageBucket bucket, String objectName);
}
