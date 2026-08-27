package com.onlinesanta.claim;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.dto.ClaimOrgView;
import com.onlinesanta.message.MessageService;
import com.onlinesanta.claim.dto.ReleaseRequest;
import com.onlinesanta.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 機構這一側的認領管理。 */
@RestController
@RequestMapping("/api/organizations/me/claims")
@Tag(name = "認領", description = "願望認領與寄送流程")
public class OrganizationClaimController {

    private final ClaimService claims;
    private final MessageService messages;
    private final CurrentUserService currentUser;

    public OrganizationClaimController(ClaimService claims,
                                       MessageService messages,
                                       CurrentUserService currentUser) {
        this.claims = claims;
        this.messages = messages;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "本機構願望的認領清單", description = "可用 status 篩選")
    public PageResponse<ClaimOrgView> list(
            @RequestParam(required = false) ClaimStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return withUnreadCounts(claims.listForMyOrganization(status, pageable));
    }

    @GetMapping("/overdue")
    @Operation(summary = "逾期未寄送的認領",
            description = "手動釋回政策的機構靠這份清單決定是否收回；自動政策的已由排程處理")
    public PageResponse<ClaimOrgView> listOverdue(
            @PageableDefault(size = 20) Pageable pageable) {
        return withUnreadCounts(claims.listOverdueForMyOrganization(pageable));
    }

    private PageResponse<ClaimOrgView> withUnreadCounts(Page<Claim> page) {
        Map<UUID, Long> unread = messages.unreadCounts(
                page.getContent().stream().map(Claim::getId).toList(),
                currentUser.require().userId());

        return PageResponse.of(page,
                claim -> ClaimOrgView.from(claim, unread.getOrDefault(claim.getId(), 0L)));
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "確認收到禮物")
    public ClaimOrgView receive(@PathVariable UUID id) {
        return ClaimOrgView.from(claims.confirmReceived(id));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "完成整段流程", description = "願望轉為 FULFILLED")
    public ClaimOrgView complete(@PathVariable UUID id) {
        return ClaimOrgView.from(claims.complete(id));
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "收回認領", description = "願望重新上架；已寄出的認領無法收回")
    public ClaimOrgView release(@PathVariable UUID id,
                                @Valid @RequestBody(required = false) ReleaseRequest request) {
        return ClaimOrgView.from(
                claims.release(id, request == null ? ReleaseRequest.empty() : request));
    }
}
