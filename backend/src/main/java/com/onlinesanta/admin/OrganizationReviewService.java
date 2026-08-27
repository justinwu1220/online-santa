package com.onlinesanta.admin;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.admin.dto.ReviewDecisionRequest;
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

    public OrganizationReviewService(OrganizationRepository organizations,
                                     OrganizationService organizationService,
                                     CurrentUserService currentUser) {
        this.organizations = organizations;
        this.organizationService = organizationService;
        this.currentUser = currentUser;
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
        return organization;
    }

    @Transactional
    public Organization reject(UUID organizationId, ReviewDecisionRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        Organization organization = organizationService.getById(organizationId);

        requireAwaitingDecision(organization);
        organization.reject(admin.userId(), request.note());
        return organization;
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
}
