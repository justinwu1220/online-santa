package com.onlinesanta.closing.dto;

import java.util.List;

/**
 * 期間選單。{@code periods} 由新到舊；{@code defaultKey} 是預設選項：
 * 涵蓋今天的檔期，沒有就選最近結束的檔期，都沒有檔期才是「尚未建立檔期」。
 */
public record PeriodListView(String defaultKey, List<OperatingPeriodView> periods) {
}
