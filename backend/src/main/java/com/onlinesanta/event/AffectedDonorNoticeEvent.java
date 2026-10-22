package com.onlinesanta.event;

import java.util.UUID;

/** 管理員要通知某筆認領的認領者（例如機構停權的說明）。內容由管理員撰寫。 */
public record AffectedDonorNoticeEvent(UUID claimId, String message) {
}
