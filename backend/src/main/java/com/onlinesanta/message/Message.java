package com.onlinesanta.message;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 捐贈者與機構在單一認領內的對話。
 *
 * <p>對話刻意綁在認領上而非使用者之間：雙方的關係只因為這份禮物而存在，
 * 沒有理由讓他們建立起脫離這個脈絡的聯繫管道。
 *
 * <p>與 {@code ClaimEvent} 同樣不繼承 BaseEntity——用 bigint 序號主鍵（天然依時間排序），
 * 且訊息送出後只有已讀狀態會變，沒有一般意義的 updated_at。
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Column(name = "sender_user_id", nullable = false, updatable = false)
    private UUID senderUserId;

    @Column(nullable = false, updatable = false, length = 2000)
    private String body;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {
        // JPA
    }

    private Message(UUID claimId, UUID senderUserId, String body) {
        this.claimId = claimId;
        this.senderUserId = senderUserId;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public static Message from(UUID claimId, UUID senderUserId, String body) {
        return new Message(claimId, senderUserId, body);
    }

    /** 標記為已讀。重複呼叫不會改寫第一次的時間。 */
    public void markRead() {
        if (readAt == null) {
            this.readAt = Instant.now();
        }
    }

    public boolean isSentBy(UUID userId) {
        return senderUserId.equals(userId);
    }

    public Long getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public UUID getSenderUserId() {
        return senderUserId;
    }

    public String getBody() {
        return body;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
