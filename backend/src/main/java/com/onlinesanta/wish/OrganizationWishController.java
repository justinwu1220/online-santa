package com.onlinesanta.wish;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.common.PageResponse;
import com.onlinesanta.wish.dto.OrganizationStageCounts;
import com.onlinesanta.wish.dto.WishOrgDetailView;
import com.onlinesanta.wish.dto.WishOrgView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 機構後台的願望清單。
 *
 * <p>獨立於 {@code /api/wishes} 之外，因為語意不同：這裡回的是「我機構的全部願望」
 * （含草稿與已下架），與公開願望牆的資料範圍完全不重疊。
 */
@RestController
@RequestMapping("/api/organizations/me/wishes")
@Tag(name = "機構", description = "機構註冊與資料維護")
public class OrganizationWishController {

    private final WishService wishes;
    private final OrganizationWishQueryService queries;

    public OrganizationWishController(WishService wishes, OrganizationWishQueryService queries) {
        this.wishes = wishes;
        this.queries = queries;
    }

    @GetMapping
    @Operation(summary = "列出自己機構的願望",
            description = "每個願望帶合併狀態 stage、目前認領摘要與未讀數；"
                    + "可用 status／year 篩選（year 以 createdAt 的台北日曆年為準）")
    public PageResponse<WishOrgView> listMine(
            @RequestParam(required = false) WishStatus status,
            @RequestParam(required = false) Integer year,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.of(queries.list(status, year, pageable), view -> view);
    }

    @GetMapping("/stage-counts")
    @Operation(summary = "篩選列各項的數量", description = "全部、草稿、審核中、可認領、已認領、已完成，以及待寄出、逾期、已寄出的細項")
    public OrganizationStageCounts counts() {
        return queries.counts();
    }

    @GetMapping("/{id}")
    @Operation(summary = "願望抽屜的資料",
            description = "願望、目前認領的完整資料、過往被收回或取消的認領紀錄；別家機構的願望一律 404")
    public WishOrgDetailView getOne(@PathVariable UUID id) {
        return queries.get(id);
    }

    @GetMapping("/years")
    @Operation(summary = "可篩選的年度清單",
            description = "自己機構的願望有 createdAt 紀錄的年份，供年度篩選下拉使用")
    public List<Integer> years() {
        return wishes.availableYears();
    }
}
