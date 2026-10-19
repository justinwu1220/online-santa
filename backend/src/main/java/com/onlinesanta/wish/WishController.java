package com.onlinesanta.wish;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import com.onlinesanta.attachment.AttachmentService;
import com.onlinesanta.claim.ClaimService;
import com.onlinesanta.common.PageResponse;
import com.onlinesanta.wish.dto.WishFilterOptions;
import com.onlinesanta.wish.dto.WishListView;
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
    private final AttachmentService attachments;
    private final ClaimService claims;

    public WishController(WishService wishes, AttachmentService attachments, ClaimService claims) {
        this.wishes = wishes;
        this.attachments = attachments;
        this.claims = claims;
    }

    // ---------------------------------------------------------------- 公開瀏覽

    @GetMapping
    @Operation(summary = "瀏覽願望牆",
            description = "預設回可認領／已認領／已完成（草稿與審核中一律不回）；"
                    + "機構、分類、年齡區間、狀態皆為選填篩選，狀態只能篩成單一值。"
                    + "清單不含示意圖網址——願望牆一律用分類圖示，圖片只在詳情頁載入")
    public PageResponse<WishListView> browse(
            @RequestParam(required = false) WishCategory category,
            @RequestParam(required = false) AgeRange ageRange,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) WishWallStatus status,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<Wish> page = wishes.browse(category, ageRange, organizationId, status, pageable);
        return PageResponse.of(page, WishListView::from);
    }

    @GetMapping("/options")
    @Operation(summary = "取得篩選選項", description = "供前端建立篩選器，避免兩端的 enum 定義不同步")
    public WishFilterOptions options() {
        return wishes.filterOptions();
    }

    @GetMapping("/{id}")
    @Operation(summary = "願望詳情")
    public WishPublicView getOne(@PathVariable UUID id) {
        Wish wish = wishes.getPublicById(id);
        return WishPublicView.from(
                wish, attachments.wishImageUrl(id), attachments.wishLetterUrls(id),
                claims.shipDeadlineDays(wish.getOrganization()),
                attachments.organizationPhotoUrl(wish.getOrganization().getId()));
    }

    // ---------------------------------------------------------------- 機構操作

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "建立願望", description = "新建的願望為草稿，需再呼叫 submit 提交審核、由管理員核准才會公開")
    public WishOrgView create(@Valid @RequestBody WishRequest request) {
        return WishOrgView.from(wishes.create(request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "修改願望內容")
    public WishOrgView update(@PathVariable UUID id, @Valid @RequestBody WishRequest request) {
        return WishOrgView.from(
                wishes.update(id, request), attachments.wishImageUrl(id), attachments.wishLetters(id));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "提交審核",
            description = "草稿 → 審核中，由管理員核准後才公開；需已上傳信件照片，機構通過審核後才能提交")
    public WishOrgView submit(@PathVariable UUID id) {
        return WishOrgView.from(
                wishes.submit(id), attachments.wishImageUrl(id), attachments.wishLetters(id));
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "撤回審核", description = "審核中 → 草稿，可以再編輯")
    public WishOrgView withdraw(@PathVariable UUID id) {
        return WishOrgView.from(
                wishes.withdraw(id), attachments.wishImageUrl(id), attachments.wishLetters(id));
    }

    @PostMapping("/{id}/begin-edit")
    @Operation(summary = "修改並重新送審：先下架",
            description = "上架中、還沒人認領的願望 → 原子地從願望牆撤下成草稿（凍結中的一併解除）；"
                    + "剛被認領則回 409。之後依草稿流程修改並提交審核，核准才重新上架")
    public WishOrgView beginEdit(@PathVariable UUID id) {
        return WishOrgView.from(
                wishes.beginEdit(id), attachments.wishImageUrl(id), attachments.wishLetters(id));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "恢復上架", description = "為修改而下架、但內容完全沒改時，直接恢復上架，不必重審")
    public WishOrgView restore(@PathVariable UUID id) {
        return WishOrgView.from(
                wishes.restore(id), attachments.wishImageUrl(id), attachments.wishLetters(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "刪除願望", description = "只有從沒公開過的草稿能刪除；曾經上架過的願望會保留")
    public void delete(@PathVariable UUID id) {
        wishes.delete(id);
    }
}
