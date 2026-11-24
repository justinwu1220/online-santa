package com.onlinesanta.closing.doc;

/**
 * 照表操作頁的 Facebook 頁籤要顯示的資訊，由 FacebookPackageBuilder 產生。
 *
 * @param text           第一則貼文的全文（含「待確認」時頁面的複製鈕會停用）
 * @param imageCount     徵信圖片總張數
 * @param posts          分成幾則貼文
 * @param firstImagePath 壓縮檔裡第一張圖片的位置（從根目錄起算）
 */
public record FacebookPost(String text, int imageCount, int posts, String firstImagePath) {
}
