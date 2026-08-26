package com.onlinesanta.claim;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.common.BaseEntity;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.organization.ReleasePolicy;
import com.onlinesanta.user.User;
import com.onlinesanta.wish.Wish;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 一筆認領，等同交易平台的訂單。 */
@Entity
@Table(name = "claims")
public class Claim extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wish_id", nullable = false, updatable = false)
    private Wish wish;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_user_id", nullable = false, updatable = false)
    private User donor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status;

    /**
     * 認領當下機構的釋回政策快照。
     *
     * <p>刻意不即時讀機構的當前設定：機構若在捐贈者認領後把政策改嚴，不該讓已經
     * 在準備禮物的人被無預警收回。政策變更只影響之後的新認領。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "release_policy_snapshot", nullable = false, updatable = false, length = 10)
    private ReleasePolicy releasePolicySnapshot;

    /**
     * 寄送期限。無論政策為何都會計算：AUTO 用機構設定的天數並據此自動釋回，
     * MANUAL 用平台預設天數，僅作為機構後台的逾期提示（M5）。
     */
    @Column(name = "ship_deadline_at")
    private Instant shipDeadlineAt;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "release_reason", length = 255)
    private String releaseReason;

    @Column(name = "tracking_carrier", length = 60)
    private String trackingCarrier;

    @Column(name = "tracking_number", length = 80)
    private String trackingNumber;

    @Column(name = "donor_message", length = 500)
    private String donorMessage;

    @Version
    @Column(nullable = false)
    private long version;

    protected Claim() {
        // JPA
    }

    private Claim(Wish wish, User donor, ReleasePolicy policy, Instant deadline, String message) {
        this.wish = wish;
        this.donor = donor;
        this.status = ClaimStatus.CLAIMED;
        this.releasePolicySnapshot = policy;
        this.shipDeadlineAt = deadline;
        this.claimedAt = Instant.now();
        this.donorMessage = message;
    }

    static Claim open(Wish wish, User donor, ReleasePolicy policy, Instant deadline, String message) {
        return new Claim(wish, donor, policy, deadline, message);
    }

    // ------------------------------------------------------------ 狀態流轉

    public void markShipped(String carrier, String trackingNumber) {
        transitionTo(ClaimStatus.SHIPPED);
        this.trackingCarrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = Instant.now();
    }

    public void markReceived() {
        transitionTo(ClaimStatus.RECEIVED);
        this.receivedAt = Instant.now();
    }

    public void markCompleted() {
        transitionTo(ClaimStatus.COMPLETED);
        this.completedAt = Instant.now();
    }

    public void release(String reason) {
        transitionTo(ClaimStatus.RELEASED);
        this.releasedAt = Instant.now();
        this.releaseReason = reason;
    }

    public void cancel(String reason) {
        transitionTo(ClaimStatus.CANCELLED);
        this.releasedAt = Instant.now();
        this.releaseReason = reason;
    }

    /**
     * 所有狀態變更的唯一入口，非法轉換一律拒絕。
     *
     * <p>錯誤碼帶上目前與目標狀態，讓前端在使用者按了過期畫面上的按鈕時，
     * 能給出「這筆認領已經寄出了」這種具體訊息，而非通用的操作失敗。
     */
    private void transitionTo(ClaimStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException("ILLEGAL_CLAIM_TRANSITION",
                    "認領目前是 %s，不能變更為 %s".formatted(status, next));
        }
        this.status = next;
    }

    // ------------------------------------------------------------ 查詢

    public boolean isOwnedBy(UUID userId) {
        return donor.getId().equals(userId);
    }

    /** 逾期未寄送。已寄出或已結束的認領不算逾期。 */
    public boolean isOverdue(Instant now) {
        return status == ClaimStatus.CLAIMED
                && shipDeadlineAt != null
                && now.isAfter(shipDeadlineAt);
    }

    public Wish getWish() {
        return wish;
    }

    public User getDonor() {
        return donor;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public ReleasePolicy getReleasePolicySnapshot() {
        return releasePolicySnapshot;
    }

    public Instant getShipDeadlineAt() {
        return shipDeadlineAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public String getTrackingCarrier() {
        return trackingCarrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getDonorMessage() {
        return donorMessage;
    }

    public long getVersion() {
        return version;
    }
}
