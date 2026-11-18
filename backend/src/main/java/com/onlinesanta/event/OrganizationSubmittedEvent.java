package com.onlinesanta.event;

import java.util.UUID;

/**
 * 機構送出參與申請（首次）或補件後重新送審。寄收件確認信到機構的 contactEmail。
 *
 * @param resubmission true＝被退件後重新送審；false＝首次申請
 */
public record OrganizationSubmittedEvent(UUID organizationId, boolean resubmission) {
}
