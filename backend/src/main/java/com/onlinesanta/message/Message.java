package com.onlinesanta.message;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** 訊息屬於哪個願望。系統訊息可能在還沒有認領時發生（願望被核准、退回），所以以願望為單位。 */
    @Column(name = "wish_id", nullable = false, updatable = false)
    private UUID wishId;

    /** 所屬的認領；願望層級的系統訊息沒有。 */
    @Column(name = "claim_id", updatable = false)
    private UUID claimId;

    /** 寄件人；系統訊息與招呼沒有。 */
    @Column(name = "sender_user_id", updatable = false)
    private UUID senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private MessageKind kind;

    @Column(nullable = false, updatable = false, length = 2000)
    private String body;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {
        // JPA
    }

    private Message(MessageKind kind, UUID wishId, UUID claimId, UUID senderUserId, String body) {
        this.kind = kind;
        this.wishId = wishId;
        this.claimId = claimId;
        this.senderUserId = senderUserId;
        this.body = body;
        this.createdAt = Instant.now();
    }

    /** 認領者與機構之間的一則訊息。 */
    public static Message user(UUID wishId, UUID claimId, UUID senderUserId, String body) {
        return new Message(MessageKind.USER, wishId, claimId, senderUserId, body);
    }

    /** 系統通知（給機構看）。{@code claimId} 可為 null，例如願望被核准或退回的時候。 */
    public static Message system(UUID wishId, UUID claimId, String body) {
        return new Message(MessageKind.SYSTEM, wishId, claimId, null, body);
    }

    /** 認領當下機構對認領者的招呼；視為雙方已讀，不計未讀。 */
    public static Message welcome(UUID wishId, UUID claimId, String body) {
        Message message = new Message(MessageKind.WELCOME, wishId, claimId, null, body);
        message.readAt = message.createdAt;
        return message;
    }

    /** 標記為已讀。重複呼叫不會改寫第一次的時間。 */
    public void markRead() {
        if (readAt == null) {
            this.readAt = Instant.now();
        }
    }

    public boolean isSentBy(UUID userId) {
        return senderUserId != null && senderUserId.equals(userId);
    }

    public Long getId() {
        return id;
    }

    public UUID getWishId() {
        return wishId;
    }

    public MessageKind getKind() {
        return kind;
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
