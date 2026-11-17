package com.onlinesanta.admin;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.admin.dto.ProfileChangeTargetView;
import com.onlinesanta.admin.dto.ProfileChangeView;
import com.onlinesanta.changelog.ProfileChangeTargetType;
import com.onlinesanta.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 管理員查閱認領者與機構的個資異動紀錄。
 *
 * <p>{@code /api/admin/**} 在 SecurityConfig 已經要求 ADMIN 角色，{@code @PreAuthorize} 是第二層。
 *
 * <p>自成一個控制器，而不是塞進 {@link AdminOverviewController}：那支已經有一組自己的
 * 批次查詢，再疊一組只會讓兩件事互相干擾。
 *
 * <p>刻意不接受 {@code Pageable}——排序固定在伺服器端（見
 * {@link AdminProfileChangeQueryService#list}），不讓 client 用 {@code ?sort=} 改動一份
 * 證據檢視的排序。page 與 size 直接收純數字。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理後台", description = "平台管理員查閱個資異動紀錄")
public class AdminProfileChangeController {

    private final AdminProfileChangeQueryService changes;

    public AdminProfileChangeController(AdminProfileChangeQueryService changes) {
        this.changes = changes;
    }

    @GetMapping("/profile-changes/targets")
    @Operation(summary = "有異動紀錄的對象清單",
            description = "依對象別列出改過資料的認領者或機構（沒有紀錄的不會出現），最近改過的在前。"
                    + "只有名稱、次數與時間，沒有欄位值，所以不寫稽核；看實際改了什麼要查單一對象的紀錄")
    public PageResponse<ProfileChangeTargetView> targets(
            @RequestParam ProfileChangeTargetType targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return PageResponse.of(changes.targets(targetType, page, size));
    }

    @GetMapping("/profile-changes")
    @Operation(summary = "個資異動紀錄",
            description = "誰在什麼時候把哪個欄位從什麼改成什麼。可依對象別、單一對象與日期區間篩選。"
                    + "日期是台灣時區，from/to 都含當天。**每次查閱都會寫入一筆稽核紀錄**")
    public PageResponse<ProfileChangeView> list(
            @RequestParam(required = false) ProfileChangeTargetType targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return PageResponse.of(changes.list(targetType, targetId, from, to, page, size));
    }
}
