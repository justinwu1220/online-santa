package com.onlinesanta.wish;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.attachment.AttachmentService;
import com.onlinesanta.common.PageResponse;
import com.onlinesanta.wish.dto.WishOrgView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 機構後台的願望清單。
 *
 * <p>獨立於 {@code /api/wishes} 之外，因為語意不同：這裡回的是「我機構的全部願望」
 * （含草稿與已下架），與公開願望牆的資料範圍完全不重疊。
 */
@RestController
@RequestMapping("/api/organizations/me/wishes")
@Tag(name = "機構", description = "機構註冊與資料維護")
public class OrganizationWishController {

    private final WishService wishes;
    private final AttachmentService attachments;

    public OrganizationWishController(WishService wishes, AttachmentService attachments) {
        this.wishes = wishes;
        this.attachments = attachments;
    }

    @GetMapping
    @Operation(summary = "列出自己機構的願望", description = "含草稿與已下架；可用 status 篩選")
    public PageResponse<WishOrgView> listMine(
            @RequestParam(required = false) WishStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<Wish> page = wishes.listMine(status, pageable);
        Map<UUID, String> imageUrls = attachments.wishImageUrls(
                page.getContent().stream().map(Wish::getId).toList());

        return PageResponse.of(page,
                wish -> WishOrgView.from(wish, imageUrls.get(wish.getId())));
    }
}
