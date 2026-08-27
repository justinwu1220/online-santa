package com.onlinesanta.admin.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationStatus;

/** 管理員審核機構時看到的資料。 */
public record OrganizationReviewView(
        UUID id,
        String name,
        String contactEmail,
        String contactPhone,
        String address,
        String description,
        OrganizationStatus status,
        String reviewNote,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant createdAt) {

    public static OrganizationReviewView from(Organization org) {
        return new OrganizationReviewView(
                org.getId(),
                org.getName(),
                org.getContactEmail(),
                org.getContactPhone(),
                org.getAddress(),
                org.getDescription(),
                org.getStatus(),
                org.getReviewNote(),
                org.getReviewedBy(),
                org.getReviewedAt(),
                org.getCreatedAt());
    }
}
