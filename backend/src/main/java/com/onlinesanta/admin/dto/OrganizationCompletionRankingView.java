package com.onlinesanta.admin.dto;

import java.util.UUID;

/** 機構完成認領排行的一列，僅管理端可見。 */
public record OrganizationCompletionRankingView(
        UUID organizationId,
        String organizationName,
        long completedCount) {
}
