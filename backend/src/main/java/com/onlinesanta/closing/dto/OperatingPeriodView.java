package com.onlinesanta.closing.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.onlinesanta.closing.OperatingPeriod;

/** 期間選單的一個選項：檔期（或「尚未建立檔期」）與它的營運週期日期。 */
public record OperatingPeriodView(String key, UUID campaignId, String name, LocalDate start, LocalDate end,
                                  boolean unassigned) {

    public static OperatingPeriodView from(OperatingPeriod period) {
        return new OperatingPeriodView(period.key(), period.campaignId(), period.name(), period.start(),
                period.end(), period.unassigned());
    }
}
