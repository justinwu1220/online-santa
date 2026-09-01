package com.onlinesanta.event;

import java.util.UUID;

/** 機構的送禮回饋照片確認上傳成功。通知該筆認領的捐贈者。 */
public record FeedbackPhotoConfirmedEvent(UUID claimId) {
}
