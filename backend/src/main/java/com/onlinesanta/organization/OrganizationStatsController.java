package com.onlinesanta.organization;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.organization.dto.OrganizationAnnualStatsView;
import com.onlinesanta.organization.dto.OrganizationMonthlyStatsView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 機構後台的統計頁面。 */
@RestController
@RequestMapping("/api/organizations/me/stats")
@Tag(name = "機構", description = "機構註冊與資料維護")
public class OrganizationStatsController {

    private final OrganizationStatsService stats;

    public OrganizationStatsController(OrganizationStatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/annual")
    @Operation(summary = "機構年度回顧",
            description = "新增願望、認領、完成率、釋回、跨年完成等年度統計，附可選年份清單")
    public OrganizationAnnualStatsView annual(@RequestParam(required = false) Integer year) {
        return stats.annual(year);
    }

    @GetMapping("/monthly")
    @Operation(summary = "機構單月每日認領分布",
            description = "年度回顧頁「每月分布」長條圖的下鑽；year、month 皆必填，month 須介於 1–12")
    public OrganizationMonthlyStatsView monthly(@RequestParam int year, @RequestParam int month) {
        return stats.monthly(year, month);
    }
}
