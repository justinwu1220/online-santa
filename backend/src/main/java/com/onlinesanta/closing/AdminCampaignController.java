package com.onlinesanta.closing;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditService;
import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.closing.dto.CampaignView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 管理員設定活動檔期（結案文件的統計與收據編號依據）。 */
@RestController
@RequestMapping("/api/admin/campaigns")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理後台", description = "活動檔期與結案文件")
public class AdminCampaignController {

    private final CampaignService campaigns;
    private final AdminAuditService audit;

    public AdminCampaignController(CampaignService campaigns, AdminAuditService audit) {
        this.campaigns = campaigns;
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "活動檔期列表", description = "新的在前")
    public List<CampaignView> list() {
        return campaigns.list().stream().map(CampaignView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增活動檔期")
    public CampaignView create(@Valid @RequestBody CampaignRequest request) {
        Campaign campaign = campaigns.create(request);
        audit.record(AdminAuditAction.SAVE_CAMPAIGN, campaign.getId(), "新增活動檔期：" + campaign.getName());
        return CampaignView.from(campaign);
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改活動檔期",
            description = "已開立收據後，收據前綴與起始號碼不能再改")
    public CampaignView update(@PathVariable UUID id, @Valid @RequestBody CampaignRequest request) {
        Campaign campaign = campaigns.update(id, request);
        audit.record(AdminAuditAction.SAVE_CAMPAIGN, campaign.getId(), "修改活動檔期：" + campaign.getName());
        return CampaignView.from(campaign);
    }
}
