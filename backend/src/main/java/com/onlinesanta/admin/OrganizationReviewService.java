package com.onlinesanta.admin;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.admin.dto.ReviewDecisionRequest;
import com.onlinesanta.admin.dto.ReviewReasonRequest;
import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.organization.OrganizationService;
import com.onlinesanta.organization.OrganizationStatus;

/**
 * 平台管理員的機構審核。
 *
 * <p>審核是必要的把關：機構上架的是孩童資料，不能讓任何人自稱機構就能發布。
 */
@Service
public class OrganizationReviewService {

    private final OrganizationRepository organizations;
    private final OrganizationService organizationService;
    private final CurrentUserService currentUser;
    private final AdminAuditService audit;

    public OrganizationReviewService(OrganizationRepository organizations,
                                     OrganizationService organizationService,
                                     CurrentUserService currentUser,
                                     AdminAuditService audit) {
        this.organizations = organizations;
        this.organizationService = organizationService;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Organization> list(OrganizationStatus status, Pageable pageable) {
        return status == null
                ? organizations.findAll(pageable)
                : organizations.findByStatusOrderByCreatedAtAsc(status, pageable);
    }

    @Transactional
    public Organization approve(UUID organizationId, ReviewDecisionRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        Organization organization = organizationService.getById(organizationId);

        requireAwaitingDecision(organization);
        organization.approve(admin.userId(), request.note());
        audit.record(AdminAuditAction.APPROVE_ORGANIZATION, organizationId, organization.getName());
        return organization;
    }

    @Transactional
    public Organization reject(UUID organizationId, ReviewReasonRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        Organization organization = organizationService.getById(organizationId);

        requireAwaitingDecision(organization);
        organization.reject(admin.userId(), request.note());
        audit.record(AdminAuditAction.REJECT_ORGANIZATION, organizationId, organization.getName());
        return organization;
    }

    /**
     * 停權：資安事件應變手冊第一步。只有 APPROVED 的機構能被停權——PENDING 還沒
     * 上架過東西、REJECTED/SUSPENDED 已經不在公開曝光裡，停權對它們沒有意義，
     * 而是暗示了誤操作。
     */
    @Transactional
    public Organization suspend(UUID organizationId, ReviewReasonRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        Organization organization = organizationService.getById(organizationId);

        requireApproved(organization);
        organization.suspend(admin.userId(), request.note());
        audit.record(AdminAuditAction.SUSPEND_ORGANIZATION, organizationId, "理由：" + request.note());
        return organization;
    }

    /** 復權：只有已停權的機構能被復權。 */
    @Transactional
    public Organization reactivate(UUID organizationId, ReviewDecisionRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        Organization organization = organizationService.getById(organizationId);

        requireSuspended(organization);
        organization.reactivate(admin.userId(), request.note());
        audit.record(AdminAuditAction.REACTIVATE_ORGANIZATION, organizationId,
                describeReason(request.note()));
        return organization;
    }

    private String describeReason(String note) {
        return note == null || note.isBlank() ? null : "理由：" + note;
    }

    /**
     * 只有待審核的機構能被裁決。
     *
     * <p>擋掉重複送出的請求——管理員在清單頁按了兩次核准，第二次應該得到明確的
     * 錯誤，而不是靜默地覆寫掉第一次的審核紀錄與時間。
     */
    private void requireAwaitingDecision(Organization organization) {
        if (organization.getStatus() != OrganizationStatus.PENDING) {
            throw new BusinessRuleException("ORGANIZATION_NOT_PENDING",
                    "這個機構目前是 %s，不在待審核狀態".formatted(organization.getStatus()));
        }
    }

    private void requireApproved(Organization organization) {
        if (organization.getStatus() != OrganizationStatus.APPROVED) {
            throw new BusinessRuleException("ORGANIZATION_NOT_APPROVED",
                    "這個機構目前是 %s，不是核准狀態，無法停權".formatted(organization.getStatus()));
        }
    }

    private void requireSuspended(Organization organization) {
        if (organization.getStatus() != OrganizationStatus.SUSPENDED) {
            throw new BusinessRuleException("ORGANIZATION_NOT_SUSPENDED",
                    "這個機構目前是 %s，不是停權狀態，無法恢復".formatted(organization.getStatus()));
        }
    }
}
