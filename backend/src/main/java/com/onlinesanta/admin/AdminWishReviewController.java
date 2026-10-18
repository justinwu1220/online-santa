package com.onlinesanta.admin;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.admin.dto.AdminWishView;
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

    public AdminWishReviewController(WishReviewService review) {
        this.review = review;
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
}
