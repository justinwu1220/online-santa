package com.onlinesanta.admin;

import java.util.List;
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
import com.onlinesanta.admin.dto.ReviewReasonRequest;
import com.onlinesanta.attachment.dto.AttachmentView;
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
    private final com.onlinesanta.suspension.OrganizationSuspensionService suspensions;

    public AdminOrganizationController(OrganizationReviewService review,
                                       com.onlinesanta.suspension.OrganizationSuspensionService suspensions) {
        this.review = review;
        this.suspensions = suspensions;
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
    @Operation(summary = "退件", description = "note 必填，說明原因；機構補件後可重新送審")
    public OrganizationReviewView reject(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewReasonRequest request) {
        return OrganizationReviewView.from(review.reject(id, request));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "停權機構",
            description = "保全原始資訊快照、機構狀態 SUSPENDED、該機構公開中的願望全部凍結（還沒寄出的認領暫停寄送期限）、"
                    + "通知機構與認領者。機構仍可登入、對話、改資料與補文件，但不能對願望操作。"
                    + "note 必填，只有 APPROVED 的機構能被停權")
    public OrganizationReviewView suspend(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewReasonRequest request) {
        return OrganizationReviewView.from(suspensions.suspend(id, request));
    }

    @GetMapping("/{id}/suspension")
    @Operation(summary = "停權處置資料",
            description = "停權當下保全的原始資訊、停權期間機構的資料修改、機構的解除申請、受影響的願望與認領數。**會寫入稽核紀錄**")
    public com.onlinesanta.suspension.SuspensionDetailView suspension(@PathVariable UUID id) {
        return suspensions.detailOf(id);
    }

    @PostMapping("/{id}/notify-donors")
    @Operation(summary = "通知受影響的認領者",
            description = "寄 Email 給這間機構所有進行中認領（含已寄出）的認領者；message 是你要說的話。**會寫入稽核紀錄**")
    public java.util.Map<String, Integer> notifyDonors(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewReasonRequest request) {
        return java.util.Map.of("notified", suspensions.notifyAffectedDonors(id, request.note()));
    }

    @PostMapping("/{id}/unlock-request/reject")
    @Operation(summary = "退回機構的解除停權申請", description = "維持停權；note 必填，機構會在團隊對話看到原因")
    public com.onlinesanta.unlock.UnlockRequestView rejectUnlock(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewReasonRequest request) {
        return suspensions.rejectUnlock(id, request.note());
    }

    @GetMapping("/{id}/documents")
    @Operation(summary = "機構申請文件", description = "機構申請時附上的立案證明文件，下載網址為限時簽章網址")
    public List<AttachmentView> documents(@PathVariable UUID id) {
        return review.documentsOf(id);
    }

    @GetMapping("/{id}/photo")
    @Operation(summary = "機構照片（點擊載入）", description = "回傳限時簽章網址（沒上傳為 null）；每次載入寫一筆稽核")
    public java.util.Map<String, String> photo(@PathVariable UUID id) {
        String url = review.photoOf(id);
        return url == null ? java.util.Map.of() : java.util.Map.of("url", url);
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "恢復機構",
            description = "停權後恢復為 APPROVED：解除「機構停權」的凍結（其他原因的凍結保留），認領者的寄送期限順延。"
                    + "只有 SUSPENDED 的機構能被恢復；機構有待審的解除申請會一併結案")
    public OrganizationReviewView reactivate(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewDecisionRequest request) {
        return OrganizationReviewView.from(
                suspensions.reactivate(id, request == null ? ReviewDecisionRequest.empty() : request));
    }
}
