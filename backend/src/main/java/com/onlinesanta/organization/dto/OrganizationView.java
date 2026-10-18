package com.onlinesanta.organization.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationStatus;
import com.onlinesanta.organization.ReleasePolicy;

/** 機構檢視自己資料時的完整視圖（含審核意見）。 */
public record OrganizationView(
        UUID id,
        String name,
        String contactPerson,
        String contactEmail,
        String contactPhone,
        String mobilePhone,
        String address,
        String preferredDeliveryTime,
        String description,
        String website,
        /** 機構照片的限時網址；還沒上傳為 null */
        String photoUrl,
        OrganizationStatus status,
        String reviewNote,
        Instant reviewedAt,
        ReleasePolicy releasePolicy,
        Integer releaseAfterDays,
        boolean canPublishWishes,
        /** 能否建立草稿。比 canPublishWishes 寬鬆——審核期間也能先準備內容 */
        boolean canDraftWishes,
        Instant createdAt) {

    public static OrganizationView from(Organization org) {
        return from(org, null);
    }

    public static OrganizationView from(Organization org, String photoUrl) {
        return new OrganizationView(
                org.getId(),
                org.getName(),
                org.getContactPerson(),
                org.getContactEmail(),
                org.getContactPhone(),
                org.getMobilePhone(),
                org.getAddress(),
                org.getPreferredDeliveryTime(),
                org.getDescription(),
                org.getWebsite(),
                photoUrl,
                org.getStatus(),
                org.getReviewNote(),
                org.getReviewedAt(),
                org.getReleasePolicy(),
                org.getReleaseAfterDays(),
                org.canPublishWishes(),
                org.canDraftWishes(),
                org.getCreatedAt());
    }
}
