package com.onlinesanta.organization;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.organization.dto.OrganizationRegistrationRequest;
import com.onlinesanta.organization.dto.OrganizationUpdateRequest;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

@Service
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final CurrentUserService currentUser;

    public OrganizationService(OrganizationRepository organizations,
                               UserRepository users,
                               CurrentUserService currentUser) {
        this.organizations = organizations;
        this.users = users;
        this.currentUser = currentUser;
    }

    /**
     * 自助註冊機構，註冊者本人成為該機構的第一位成員。
     *
     * <p>註冊後狀態為 PENDING，須待管理員核准才能上架願望——避免任何人自稱機構就能
     * 發布孩童資料。
     */
    @Transactional
    public Organization register(OrganizationRegistrationRequest request) {
        // 申請通過後就能上架孩童資料，門檻不能只是「填了一個信箱」
        AppPrincipal principal = currentUser.requireVerifiedEmail();

        if (principal.organizationId() != null) {
            throw new BusinessRuleException("ALREADY_IN_ORGANIZATION", "你已隸屬於其他機構");
        }
        if (principal.isAdmin()) {
            throw new BusinessRuleException("ADMIN_CANNOT_REGISTER_ORG",
                    "平台管理員不可註冊機構，以免球員兼裁判");
        }
        // 名稱唯一是為了讓捐贈者能辨識機構；資料庫的 uq_organizations_name 是最終保證，
        // 這裡先擋是為了回傳可讀的訊息
        if (organizations.existsByNameIgnoreCase(request.name())) {
            throw new BusinessRuleException("ORGANIZATION_NAME_TAKEN", "這個機構名稱已被註冊");
        }

        Organization organization = organizations.save(Organization.register(
                request.name(),
                request.contactEmail(),
                request.contactPhone(),
                request.address(),
                request.description()));

        User user = users.findById(principal.userId())
                .orElseThrow(() -> ResourceNotFoundException.of("使用者", principal.userId()));
        user.joinOrganization(organization.getId());

        return organization;
    }

    @Transactional(readOnly = true)
    public Organization getMine() {
        return getById(currentUser.requireOrganizationId());
    }

    @Transactional
    public Organization updateMine(OrganizationUpdateRequest request) {
        Organization organization = getById(currentUser.requireOrganizationId());

        if (!organization.getName().equalsIgnoreCase(request.name())
                && organizations.existsByNameIgnoreCase(request.name())) {
            throw new BusinessRuleException("ORGANIZATION_NAME_TAKEN", "這個機構名稱已被註冊");
        }

        organization.updateProfile(
                request.name(),
                request.contactEmail(),
                request.contactPhone(),
                request.address(),
                request.description());
        organization.updateReleasePolicy(request.releasePolicy(), request.releaseAfterDays());

        return organization;
    }

    /** 被退件的機構補件後重新送審。 */
    @Transactional
    public Organization resubmitMine() {
        Organization organization = getById(currentUser.requireOrganizationId());
        organization.resubmitForReview();
        return organization;
    }

    @Transactional(readOnly = true)
    public Organization getById(UUID id) {
        return organizations.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("機構", id));
    }
}
