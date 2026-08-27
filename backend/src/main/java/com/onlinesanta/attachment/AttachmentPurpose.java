package com.onlinesanta.attachment;

import com.onlinesanta.storage.StorageBucket;

/**
 * 附件的用途。對應 {@code attachments.owner_type} 的 CHECK 約束。
 *
 * <p>用途決定三件事：存到哪個 bucket、誰能上傳、誰能看。把這三者綁在同一個列舉上，
 * 新增一種附件時就不會漏掉其中一項。
 */
public enum AttachmentPurpose {

    /** 禮物示意圖，由機構上傳，掛在願望上。不含孩童影像，可公開。 */
    WISH_IMAGE(StorageBucket.PUBLIC, "wishes", 1),

    /** 寄送證明，由捐贈者上傳，掛在認領上。可能含姓名地址，須限時簽章才能讀。 */
    SHIPPING_PROOF(StorageBucket.PRIVATE, "shipping-proofs", 5),

    /** 送禮回饋照片，由機構上傳，掛在認領上。可能含孩童影像，敏感度最高。 */
    ORG_FEEDBACK(StorageBucket.PRIVATE, "feedback", 5);

    private final StorageBucket bucket;
    private final String prefix;
    private final int maxPerOwner;

    AttachmentPurpose(StorageBucket bucket, String prefix, int maxPerOwner) {
        this.bucket = bucket;
        this.prefix = prefix;
        this.maxPerOwner = maxPerOwner;
    }

    public StorageBucket bucket() {
        return bucket;
    }

    public String prefix() {
        return prefix;
    }

    /** 單一擁有者可保留的附件數上限。 */
    public int maxPerOwner() {
        return maxPerOwner;
    }

    /** 是否為「只保留最新一張」——願望示意圖換新時舊的直接汰除。 */
    public boolean replacesPrevious() {
        return maxPerOwner == 1;
    }

    public boolean isPubliclyReadable() {
        return bucket == StorageBucket.PUBLIC;
    }
}
