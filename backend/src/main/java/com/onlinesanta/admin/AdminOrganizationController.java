package com.onlinesanta.admin;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.admin.dto.OrganizationReviewView;
import com.onlinesanta.admin.dto.ReviewDecisionRequest;
import com.onlinesanta.common.PageResponse;
import com.onlinesanta.organization.OrganizationStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 機構審核後台。
 *
 * <p>{@code /api/admin/**} 在 SecurityConfig 已經要求 ADMIN 角色，這裡的
 * {@code @PreAuthorize} 是第二層：日後若有人調整了路徑規則，方法層的宣告仍然守著。
 */
@RestController
@RequestMapping("/api/admin/organizations")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理後台", description = "平台管理員的機構審核")
public class AdminOrganizationController {

    private final OrganizationReviewService review;

    public AdminOrganizationController(OrganizationReviewService review) {
        this.review = review;
    }

    @GetMapping
    @Operation(summary = "機構清單", description = "可用 status 篩選，例如只看 PENDING")
    public PageResponse<OrganizationReviewView> list(
            @RequestParam(required = false) OrganizationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(review.list(status, pageable), OrganizationReviewView::from);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "核准機構", description = "核准後該機構即可上架願望")
    public OrganizationReviewView approve(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewDecisionRequest request) {
        return OrganizationReviewView.from(
                review.approve(id, request == null ? ReviewDecisionRequest.empty() : request));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "退件", description = "請於 note 說明原因；機構補件後可重新送審")
    public OrganizationReviewView reject(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewDecisionRequest request) {
        return OrganizationReviewView.from(
                review.reject(id, request == null ? ReviewDecisionRequest.empty() : request));
    }
}
