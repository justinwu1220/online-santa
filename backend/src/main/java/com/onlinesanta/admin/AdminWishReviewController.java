package com.onlinesanta.admin;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.admin.dto.AdminWishCounts;
import com.onlinesanta.admin.dto.AdminWishDetailView;
import com.onlinesanta.admin.dto.AdminWishListItem;
import com.onlinesanta.admin.dto.AdminWishView;
import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.common.PageResponse;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.onlinesanta.admin.dto.WishRejectRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 願望審核：核准上架或退回。 */
@RestController
@RequestMapping("/api/admin/wishes")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理後台", description = "平台管理員的願望審核")
public class AdminWishReviewController {

    private final WishReviewService review;
    private final AdminWishQueryService query;

    public AdminWishReviewController(WishReviewService review, AdminWishQueryService query) {
        this.review = review;
        this.query = query;
    }

    @GetMapping
    @Operation(summary = "願望清單", description = "草稿以外的所有願望；filter 為 ALL／REVIEW／OPEN／CLAIMED／LATE／DONE／FROZEN，q 搜尋禮物、孩子暱稱或機構")
    public PageResponse<AdminWishListItem> list(
            @RequestParam(defaultValue = "ALL") AdminWishQueryService.Filter filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer year,
            @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(query.list(filter, q, year, pageable), item -> item);
    }

    @GetMapping("/counts")
    @Operation(summary = "篩選列各項數量")
    public AdminWishCounts counts() {
        return query.counts();
    }

    @GetMapping("/{id}")
    @Operation(summary = "願望抽屜資料", description = "含禮物價格、機構卡、目前認領（捐贈人姓名與帳號信箱）；有認領者時寫一筆稽核")
    public AdminWishDetailView detail(@PathVariable UUID id) {
        return query.get(id);
    }

    @GetMapping("/{id}/files")
    @Operation(summary = "願望附件（點擊載入）", description = "孩子的手寫信件與目前認領的寄件照片；每次載入寫一筆稽核")
    public List<AttachmentView> files(@PathVariable UUID id) {
        return query.files(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "核准願望", description = "審核中 → 可認領，立即出現在願望牆")
    public AdminWishView approve(@PathVariable UUID id) {
        return AdminWishView.from(review.approve(id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "退回願望", description = "審核中 → 草稿；note 必填（至少 5 個字），機構會看到原因")
    public AdminWishView reject(@PathVariable UUID id, @Valid @RequestBody WishRejectRequest request) {
        return AdminWishView.from(review.reject(id, request));
    }

    @PostMapping("/{id}/freeze")
    @Operation(summary = "暫時凍結願望",
            description = "願望從願望牆與認領者頁面消失（機構與管理員後台仍保留）；note 必填（至少 4 個字）")
    public AdminWishView freeze(@PathVariable UUID id,
                                @Valid @RequestBody com.onlinesanta.admin.dto.WishFreezeRequest request) {
        return AdminWishView.from(review.freeze(id, request));
    }

    @PostMapping("/{id}/unfreeze")
    @Operation(summary = "解除凍結", description = "願望回到凍結前的狀態並重新顯示")
    public AdminWishView unfreeze(@PathVariable UUID id) {
        return AdminWishView.from(review.unfreeze(id));
    }
}
