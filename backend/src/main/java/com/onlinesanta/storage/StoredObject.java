package com.onlinesanta.storage;

/** 儲存空間中一個已存在物件的中繼資料。 */
public record StoredObject(String objectName, String contentType, long sizeBytes) {
}
