package com.onlinesanta.common.dto;

/**
 * 一個欄位的變更（舊 → 新）。願望的「修改後重審」與個資異動紀錄共用。
 *
 * <p>{@code field} 在願望那邊是中文標籤（後端產生），在個資異動紀錄那邊是機器欄位名
 * （中文標籤留在前端）——兩者都刻意維持現狀：願望的標籤是審核當下的說明文字，而異動
 * 紀錄是證據，標籤日後被改寫時既有紀錄的意思不該跟著變。
 *
 * <p>{@code before} / {@code after} 存實際的值，不做正規化；要不要算「有變動」由
 * {@link com.onlinesanta.common.FieldDiff} 判斷。
 */
public record FieldChange(String field, String before, String after) {
}
