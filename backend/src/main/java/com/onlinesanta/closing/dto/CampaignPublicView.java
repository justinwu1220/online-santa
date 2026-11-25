package com.onlinesanta.closing.dto;

/**
 * 公開的檔期資訊：目前只有網站頁尾要顯示的勸募許可文號。
 *
 * <p>其餘欄位（活動編號、收據前綴與流水號、各種日期、協會機構）只在後台看得到，
 * 刻意不放進來——這支端點未登入也打得到。
 *
 * @param permitNo 勸募許可文號；還沒有任何檔期時是 null，頁尾那一行就不顯示
 */
public record CampaignPublicView(String permitNo) {
}
