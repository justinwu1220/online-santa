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
    private final com.onlinesanta.suspension.OrganizationSuspensionService suspensions;

    public OrganizationController(OrganizationService organizations,
                                  AttachmentService attachments,
                                  CurrentUserService currentUser,
                                  com.onlinesanta.suspension.OrganizationSuspensionService suspensions) {
        this.organizations = organizations;
        this.attachments = attachments;
        this.currentUser = currentUser;
        this.suspensions = suspensions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "自助註冊機構", description = "註冊後狀態為 PENDING，須待管理員核准才能上架願望")
    public OrganizationView register(@Valid @RequestBody OrganizationRegistrationRequest request) {
        Organization created = organizations.register(request);
        return OrganizationView.from(created, attachments.organizationPhotoUrl(created.getId()));
    }

    @GetMapping("/me")
    @Operation(summary = "取得自己機構的資料")
    public OrganizationView getMine() {
        Organization mine = organizations.getMine();
        return OrganizationView.from(mine, attachments.organizationPhotoUrl(mine.getId()));
    }

    @PatchMapping("/me")
    @Operation(summary = "更新機構資料與逾期釋回政策")
    public OrganizationView updateMine(@Valid @RequestBody OrganizationUpdateRequest request) {
        Organization updated = organizations.updateMine(request);
        return OrganizationView.from(updated, attachments.organizationPhotoUrl(updated.getId()));
    }

    @GetMapping("/me/unlock-request")
    @Operation(summary = "我的解除停權申請", description = "最近一筆（待審或已決定）；沒申請過為 null")
    public com.onlinesanta.unlock.UnlockRequestView unlockRequest() {
        return suspensions.latestForMyOrganization().orElse(null);
    }

    @PostMapping("/me/unlock-request")
    @Operation(summary = "申請解除停權", description = "資料修改好、文件補上之後申請；同時只能有一筆待審")
    public com.onlinesanta.unlock.UnlockRequestView requestUnlock(
            @Valid @RequestBody(required = false) com.onlinesanta.unlock.UnlockRequestBody body) {
        return suspensions.requestUnlock(body == null ? null : body.message());
    }

    @PostMapping("/me/unlock-request/withdraw")
    @Operation(summary = "撤回解除停權申請")
    public com.onlinesanta.unlock.UnlockRequestView withdrawUnlock() {
        return suspensions.withdrawUnlock();
    }

    @PostMapping("/me/resubmit")
    @Operation(summary = "補件後重新送審")
    public OrganizationView resubmit() {
        Organization resubmitted = organizations.resubmitMine();
        return OrganizationView.from(resubmitted, attachments.organizationPhotoUrl(resubmitted.getId()));
    }

    @GetMapping("/me/documents")
    @Operation(summary = "取得自己機構上傳的申請文件", description = "私密 bucket，下載網址為限時簽章網址")
    public List<AttachmentView> myDocuments() {
        return attachments.organizationDocuments(currentUser.requireOrganizationId());
    }
}
