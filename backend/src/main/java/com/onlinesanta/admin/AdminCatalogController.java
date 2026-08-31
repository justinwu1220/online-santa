package com.onlinesanta.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.admin.dto.AdminClaimView;
import com.onlinesanta.admin.dto.AdminWishView;
import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.claim.dto.ClaimEventView;
import com.onlinesanta.common.PageResponse;
import com.onlinesanta.wish.WishStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 監控中心的跨機構唯讀檢視。
 *
 * <p>{@code /api/admin/**} 在 SecurityConfig 已要求 ADMIN 角色；這裡的
 * {@code @PreAuthorize} 是第二層，日後若有人調整路徑規則，方法層仍然守著。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "監控中心", description = "跨機構的唯讀檢視。存取個人資料會寫入稽核紀錄")
public class AdminCatalogController {

    private final AdminCatalogService catalog;

    public AdminCatalogController(AdminCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/wishes")
    @Operation(summary = "跨機構願望清單",
            description = "含所有狀態，可用 status／year 篩選（year 以 createdAt 的台北日曆年為準）")
    public PageResponse<AdminWishView> wishes(
            @RequestParam(required = false) WishStatus status,
            @RequestParam(required = false) Integer year,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.of(catalog.listWishes(status, year, pageable), AdminWishView::from);
    }

    @GetMapping("/claims")
    @Operation(summary = "跨機構認領清單", description = "可用 status 篩選，或 overdue=true 只看逾期")
    public PageResponse<AdminClaimView> claims(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(defaultValue = "false") boolean overdue,
            @PageableDefault(size = 20, sort = "claimedAt") Pageable pageable) {
        return PageResponse.of(
                catalog.listClaims(status, overdue, pageable), AdminClaimView::from);
    }

    @GetMapping("/claims/{id}")
    @Operation(summary = "認領詳情",
            description = "含捐贈者聯絡資訊。**這個操作會寫入稽核紀錄**")
    public AdminClaimView claim(@PathVariable UUID id) {
        return AdminClaimView.from(catalog.getClaim(id));
    }

    @GetMapping("/claims/{id}/timeline")
    @Operation(summary = "認領歷程")
    public List<ClaimEventView> timeline(@PathVariable UUID id) {
        return catalog.timelineOf(id).stream().map(ClaimEventView::from).toList();
    }

    @GetMapping("/claims/{id}/attachments")
    @Operation(summary = "認領的附件",
            description = "含寄送證明與可能有孩童影像的回饋照片。**這個操作會寫入稽核紀錄**")
    public List<AttachmentView> attachments(@PathVariable UUID id) {
        return catalog.attachmentsOf(id);
    }
}
