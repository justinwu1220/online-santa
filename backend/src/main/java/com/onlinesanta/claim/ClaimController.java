package com.onlinesanta.claim;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.claim.dto.ClaimDonorView;
import com.onlinesanta.claim.dto.ClaimEventView;
import com.onlinesanta.claim.dto.ClaimRequest;
import com.onlinesanta.claim.dto.ReleaseRequest;
import com.onlinesanta.claim.dto.ShipRequest;
import com.onlinesanta.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 捐贈者這一側的認領操作。 */
@RestController
@RequestMapping("/api")
@Tag(name = "認領", description = "願望認領與寄送流程")
public class ClaimController {

    private final ClaimService claims;

    public ClaimController(ClaimService claims) {
        this.claims = claims;
    }

    @PostMapping("/wishes/{wishId}/claim")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "認領願望",
            description = "願望已被他人領走時回 409（errorCode = WISH_ALREADY_CLAIMED）")
    public ClaimDonorView claim(@PathVariable UUID wishId,
                                @Valid @RequestBody(required = false) ClaimRequest request) {
        return ClaimDonorView.from(
                claims.claim(wishId, request == null ? ClaimRequest.empty() : request));
    }

    @GetMapping("/claims/me")
    @Operation(summary = "我的認領清單")
    public PageResponse<ClaimDonorView> listMine(
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(claims.listMine(pageable), ClaimDonorView::from);
    }

    @GetMapping("/claims/{id}")
    @Operation(summary = "認領詳情", description = "僅該筆認領的捐贈者與願望所屬機構可見")
    public ClaimDonorView getOne(@PathVariable UUID id) {
        return ClaimDonorView.from(claims.getVisibleById(id));
    }

    @GetMapping("/claims/{id}/timeline")
    @Operation(summary = "認領歷程")
    public List<ClaimEventView> timeline(@PathVariable UUID id) {
        return claims.timelineOf(id).stream().map(ClaimEventView::from).toList();
    }

    @PostMapping("/claims/{id}/ship")
    @Operation(summary = "回報已寄出")
    public ClaimDonorView ship(@PathVariable UUID id, @Valid @RequestBody ShipRequest request) {
        return ClaimDonorView.from(claims.ship(id, request));
    }

    @PostMapping("/claims/{id}/cancel")
    @Operation(summary = "取消認領", description = "願望會立刻重新上架；已寄出的認領無法取消")
    public ClaimDonorView cancel(@PathVariable UUID id,
                                 @Valid @RequestBody(required = false) ReleaseRequest request) {
        return ClaimDonorView.from(
                claims.cancel(id, request == null ? ReleaseRequest.empty() : request));
    }
}
