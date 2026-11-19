package com.onlinesanta.closing.doc;

/**
 * 待補資料的一項：缺什麼、誰提供、影響哪個輸出、目前可以先完成什麼。
 * 結案文件缺的資料一律列在這裡（壓縮檔裡的 {@code 待補資料.md}），不填 0、不編造。
 */
public record TodoItem(String missing, String owner, String affects, String canDoNow) {
}
