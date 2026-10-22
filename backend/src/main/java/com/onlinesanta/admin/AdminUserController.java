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

import com.onlinesanta.admin.dto.ReviewDecisionRequest;
import com.onlinesanta.admin.dto.ReviewReasonRequest;
import com.onlinesanta.common.PageResponse;
import com.onlinesanta.suspension.AdminUserDetailView;
import com.onlinesanta.suspension.AdminUserView;
import com.onlinesanta.suspension.UserSuspensionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 管理員處置認領者違規：停權（受限登入）、解除停權、釋出認領。
 *
 * <p>{@code /api/admin/**} 在 SecurityConfig 已經要求 ADMIN 角色，{@code @PreAuthorize} 是第二層。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理後台", description = "平台管理員的認領者處置")
public class AdminUserController {

    private final UserSuspensionService suspensions;

    public AdminUserController(UserSuspensionService suspensions) {
        this.suspensions = suspensions;
    }

    @GetMapping("/users")
    @Operation(summary = "認領者清單", description = "只列一般認領者；q 搜尋信箱、顯示名稱或真實姓名")
    public PageResponse<AdminUserView> list(@RequestParam(required = false) String q,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(suspensions.search(q, pageable), user -> user);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "認領者處置資料",
            description = "真實姓名、進行中的認領與停權當下保全的資訊。**這個操作會寫入稽核紀錄**")
    public AdminUserDetailView detail(@PathVariable UUID id) {
        return suspensions.detailOf(id);
    }

    @PostMapping("/users/{id}/suspend")
    @Operation(summary = "停權認領者",
            description = "受限登入：仍可登入看停權通知，所有寫入動作被擋；他進行中認領的願望凍結，機構後台標示。"
                    + "note 必填；只有一般認領者能被停權")
    public AdminUserView suspend(@PathVariable UUID id, @Valid @RequestBody ReviewReasonRequest request) {
        return suspensions.suspend(id, request.note());
    }

    @PostMapping("/users/{id}/reactivate")
    @Operation(summary = "解除認領者停權", description = "功能恢復；因停權而凍結的願望解除，寄送期限順延。附註選填")
    public AdminUserView reactivate(@PathVariable UUID id,
                                    @RequestBody(required = false) ReviewDecisionRequest request) {
        return suspensions.reinstate(id, request == null ? null : request.note());
    }

    @PostMapping("/claims/{id}/release")
    @Operation(summary = "釋出認領",
            description = "只有還沒寄出的認領。認領結束、願望回到願望牆，認領者收到通知，機構留下系統訊息；note 必填")
    public void releaseClaim(@PathVariable UUID id, @Valid @RequestBody ReviewReasonRequest request) {
        suspensions.releaseClaim(id, request.note());
    }
}
