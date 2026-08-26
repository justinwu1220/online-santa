package com.onlinesanta.common;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 所有 entity 的共同基底：UUID 主鍵與稽核時間戳。
 *
 * <p>主鍵由 Hibernate 在 Java 端產生（{@link GenerationType#UUID}），而非依賴資料庫的
 * {@code gen_random_uuid()} 預設值——這樣在 flush 之前就能拿到 id，關聯設定較單純。
 * 資料庫的預設值仍保留，作為繞過 JPA 直接插入時的保護。
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 以 id 判斷相等。id 為 null（尚未持久化）時退回同一性比較，避免兩個新建立的
     * 物件被誤判為相同。
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that) || !getClass().equals(other.getClass())) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass());
    }
}
