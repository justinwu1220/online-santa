package com.onlinesanta.organization;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.attachment.AttachmentService;
import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.organization.dto.OrganizationRegistrationRequest;
import com.onlinesanta.organization.dto.OrganizationUpdateRequest;
import com.onlinesanta.organization.dto.OrganizationView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations")
@Tag(name = "機構", description = "機構註冊與資料維護")
public class OrganizationController {

    private final OrganizationService organizations;
    private final AttachmentService attachments;
    private final CurrentUserService currentUser;

    public OrganizationController(OrganizationService organizations,
                                  AttachmentService attachments,
                                  CurrentUserService currentUser) {
        this.organizations = organizations;
        this.attachments = attachments;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "自助註冊機構", description = "註冊後狀態為 PENDING，須待管理員核准才能上架願望")
    public OrganizationView register(@Valid @RequestBody OrganizationRegistrationRequest request) {
        return OrganizationView.from(organizations.register(request));
    }

    @GetMapping("/me")
    @Operation(summary = "取得自己機構的資料")
    public OrganizationView getMine() {
        return OrganizationView.from(organizations.getMine());
    }

    @PatchMapping("/me")
    @Operation(summary = "更新機構資料與逾期釋回政策")
    public OrganizationView updateMine(@Valid @RequestBody OrganizationUpdateRequest request) {
        return OrganizationView.from(organizations.updateMine(request));
    }

    @PostMapping("/me/resubmit")
    @Operation(summary = "補件後重新送審")
    public OrganizationView resubmit() {
        return OrganizationView.from(organizations.resubmitMine());
    }

    @GetMapping("/me/documents")
    @Operation(summary = "取得自己機構上傳的申請文件", description = "私密 bucket，下載網址為限時簽章網址")
    public List<AttachmentView> myDocuments() {
        return attachments.organizationDocuments(currentUser.requireOrganizationId());
    }
}
