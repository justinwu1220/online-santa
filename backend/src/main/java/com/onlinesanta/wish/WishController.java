package com.onlinesanta.wish;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.common.PageResponse;
import com.onlinesanta.wish.dto.WishFilterOptions;
import com.onlinesanta.wish.dto.WishOrgView;
import com.onlinesanta.wish.dto.WishPublicView;
import com.onlinesanta.wish.dto.WishRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 願望的公開瀏覽與機構管理。
 *
 * <p>兩組端點共用 {@code /api/wishes} 路徑但回傳不同型別的視圖：公開的 GET 回
 * {@link WishPublicView}，機構的寫入操作回 {@link WishOrgView}。
 */
@RestController
@RequestMapping("/api/wishes")
@Tag(name = "願望", description = "願望瀏覽與機構端的願望管理")
public class WishController {

    private final WishService wishes;

    public WishController(WishService wishes) {
        this.wishes = wishes;
    }

    // ---------------------------------------------------------------- 公開瀏覽

    @GetMapping
    @Operation(summary = "瀏覽願望牆", description = "只回上架中的願望；分類、年齡區間、價格區間皆為選填篩選")
    public PageResponse<WishPublicView> browse(
            @RequestParam(required = false) WishCategory category,
            @RequestParam(required = false) AgeRange ageRange,
            @RequestParam(required = false) PriceRange priceRange,
            @PageableDefault(size = 20, sort = "publishedAt") Pageable pageable) {
        return PageResponse.of(
                wishes.browse(category, ageRange, priceRange, pageable),
                WishPublicView::from);
    }

    @GetMapping("/options")
    @Operation(summary = "取得篩選選項", description = "供前端建立篩選器，避免兩端的 enum 定義不同步")
    public WishFilterOptions options() {
        return WishFilterOptions.build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "願望詳情")
    public WishPublicView getOne(@PathVariable UUID id) {
        return WishPublicView.from(wishes.getPublicById(id));
    }

    // ---------------------------------------------------------------- 機構操作

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "建立願望", description = "新建的願望為草稿，需再呼叫 publish 才會公開")
    public WishOrgView create(@Valid @RequestBody WishRequest request) {
        return WishOrgView.from(wishes.create(request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "修改願望內容")
    public WishOrgView update(@PathVariable UUID id, @Valid @RequestBody WishRequest request) {
        return WishOrgView.from(wishes.update(id, request));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "上架願望")
    public WishOrgView publish(@PathVariable UUID id) {
        return WishOrgView.from(wishes.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    @Operation(summary = "下架願望", description = "已被認領的願望無法下架")
    public WishOrgView unpublish(@PathVariable UUID id) {
        return WishOrgView.from(wishes.unpublish(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "刪除願望", description = "只有草稿能刪除；已公開過的請改用下架")
    public void delete(@PathVariable UUID id) {
        wishes.delete(id);
    }
}
