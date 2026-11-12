package com.onlinesanta.changelog;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.onlinesanta.user.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 一筆個資異動紀錄：誰、什麼時候、把哪些欄位從什麼改成什麼。
 *
 * <p>只增不改，所有欄位都是 {@code updatable = false}；資料庫那邊還有 trigger 擋
 * UPDATE/DELETE（見 V43）。比照 {@link com.onlinesanta.admin.AdminAuditLog} 刻意不繼承
 * BaseEntity——那會帶來 updatedAt 這種對 append-only 紀錄沒有意義、還會誤導的欄位。
 *
 * <p>{@code createdAt} 交給資料庫的 {@code DEFAULT now()}，不用 JVM 的 Instant.now()：
 * 多個執行個體時鐘偏移會寫出順序錯亂的列。因為 {@code now()} 是交易開始時間，讀取時
 * 排序一律要再帶 id。
 */
@Entity
@Table(name = "profile_change_logs")
public class ProfileChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    private ProfileChangeTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    /** JSON 陣列：[{"field":..,"before":..,"after":..}]。存機器欄位名，中文標籤在前端。 */
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String changes;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    /** 寫入當下的 email。刻意去正規化保存，讓紀錄指得出「當時」是誰。 */
    @Column(name = "actor_email", updatable = false, length = 255)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", updatable = false, length = 20)
    private UserRole actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ProfileChangeSource source;

    /**
     * 時間以資料庫為準（欄位的 DEFAULT now()），不是 JVM 的 Instant.now()——多個執行個體
     * 時鐘偏移會寫出順序錯亂的列。{@code @Generated} 讓 Hibernate 在 INSERT 之後把值讀
     * 回來，否則記憶體裡這個欄位會一直是 null。
     *
     * <p>注意 {@code now()} 是交易開始時間，同一個交易寫出的兩筆會完全相同，所以查詢
     * 排序一律要再帶 id。
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ProfileChangeLog() {
    }

    private ProfileChangeLog(ProfileChangeTargetType targetType, UUID targetId, String changes,
                             ProfileChangeActor actor) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.changes = changes;
        this.actorUserId = actor.userId();
        this.actorEmail = actor.email();
        this.actorRole = actor.role();
        this.source = actor.source();
    }

    public static ProfileChangeLog of(ProfileChangeTargetType targetType, UUID targetId,
                                      String changes, ProfileChangeActor actor) {
        return new ProfileChangeLog(targetType, targetId, changes, actor);
    }

    public Long getId() {
        return id;
    }

    public ProfileChangeTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getChanges() {
        return changes;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public UserRole getActorRole() {
        return actorRole;
    }

    public ProfileChangeSource getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
