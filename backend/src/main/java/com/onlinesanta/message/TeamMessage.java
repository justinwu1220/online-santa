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
 * 機構與平台團隊的對話（SPEC 15.6）。每個機構一條對話串，與任何認領無關。
 * 已讀狀態是「收件的那一方」讀了沒有：團隊寄的由機構讀，機構寄的由團隊讀。
 */
@Entity
@Table(name = "team_messages")
public class TeamMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "from_team", nullable = false, updatable = false)
    private boolean fromTeam;

    @Column(name = "sender_user_id", updatable = false)
    private UUID senderUserId;

    @Column(nullable = false, updatable = false, length = 2000)
    private String body;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TeamMessage() {
        // JPA
    }

    private TeamMessage(UUID organizationId, boolean fromTeam, UUID senderUserId, String body) {
        this.organizationId = organizationId;
        this.fromTeam = fromTeam;
        this.senderUserId = senderUserId;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public static TeamMessage fromOrganization(UUID organizationId, UUID senderUserId, String body) {
        return new TeamMessage(organizationId, false, senderUserId, body);
    }

    public static TeamMessage fromTeam(UUID organizationId, UUID adminUserId, String body) {
        return new TeamMessage(organizationId, true, adminUserId, body);
    }

    public void markRead() {
        if (readAt == null) {
            this.readAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public boolean isFromTeam() {
        return fromTeam;
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
