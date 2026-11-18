package com.onlinesanta.organization;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.closing.OperatingPeriodService;
import com.onlinesanta.closing.dto.PeriodListView;
import com.onlinesanta.organization.dto.OrganizationPeriodStatsView;
import com.onlinesanta.organization.dto.OrganizationMonthlyStatsView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 機構後台的統計頁面。 */
@RestController
@RequestMapping("/api/organizations/me/stats")
@Tag(name = "機構", description = "機構註冊與資料維護")
public class OrganizationStatsController {

    private final OrganizationStatsService stats;
    private final OperatingPeriodService periods;
    private final CurrentUserService currentUser;

    public OrganizationStatsController(OrganizationStatsService stats, OperatingPeriodService periods,
                                       CurrentUserService currentUser) {
        this.stats = stats;
        this.periods = periods;
        this.currentUser = currentUser;
    }

    @GetMapping("/periods")
    @Operation(summary = "檔期選單", description = "各檔期的營運週期，由新到舊，附預設選項")
    public PeriodListView periods() {
        currentUser.requireOrganizationId();
        return periods.list();
    }

    @GetMapping("/period")
    @Operation(summary = "機構檔期回顧",
            description = "新增願望、認領、完成率、釋回、期間結束後完成等檔期統計；period 為檔期 id，省略時用預設選項")
    public OrganizationPeriodStatsView period(@RequestParam(required = false) String period) {
        currentUser.requireOrganizationId();
        return stats.period(periods.resolve(period));
    }

    @GetMapping("/monthly")
    @Operation(summary = "機構單月每日認領分布",
            description = "檔期回顧頁「每月分布」長條圖的下鑽；year、month 皆必填，month 須介於 1–12")
    public OrganizationMonthlyStatsView monthly(@RequestParam int year, @RequestParam int month) {
        return stats.monthly(year, month);
    }
}
