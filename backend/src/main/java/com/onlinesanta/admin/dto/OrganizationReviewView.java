package com.onlinesanta.admin.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationStatus;

/** 管理員審核機構時看到的資料。 */
public record OrganizationReviewView(
        UUID id,
        String name,
        String contactPerson,
        String responsiblePerson,
        String withholdingTaxId,
        String contactEmail,
        String contactPhone,
        String mobilePhone,
        String address,
        String preferredDeliveryTime,
        String description,
        String website,
        OrganizationStatus status,
        String reviewNote,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        /** 停權中的機構送出了解除停權申請，等管理員審核。只有清單會算，決策端點一律 false */
        boolean unlockRequested) {

    /**
     * 給決策端點（核准／退回／停權／解除停權）用：它們回傳的是剛做完決定的那一刻，
     * 不可能留著待審的解除申請——核准與退回只作用在待審核的機構，停權當下申請還不存在，
     * 而解除停權會把待審申請一併結掉（見 OrganizationSuspensionService.reactivate）。
     */
    public static OrganizationReviewView from(Organization org) {
        return from(org, false);
    }

    public static OrganizationReviewView from(Organization org, boolean unlockRequested) {
        return new OrganizationReviewView(
                org.getId(),
                org.getName(),
                org.getContactPerson(),
                org.getResponsiblePerson(),
                org.getWithholdingTaxId(),
                org.getContactEmail(),
                org.getContactPhone(),
                org.getMobilePhone(),
                org.getAddress(),
                org.getPreferredDeliveryTime(),
                org.getDescription(),
                org.getWebsite(),
                org.getStatus(),
                org.getReviewNote(),
                org.getReviewedBy(),
                org.getReviewedAt(),
                org.getCreatedAt(),
                unlockRequested);
    }
}
