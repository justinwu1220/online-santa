package com.onlinesanta.user;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 本系統自己管理的帳號（密碼、信箱驗證、登入鎖定都在這裡）。
 *
 * <p>授權判斷一律以本表為準，而不是寫在 token 裡的 claims：token 在有效期內不會更新，
 * 會讓「Admin 剛核准的機構」在那段期間無法操作。
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", length = 100)
    private String displayName;

    /**
     * 真實姓名，用於捐贈收據與徵信名冊。不是 displayName——後者會顯示給機構，
     * 這個欄位不得出現在任何對外的回應裡，只有本人的個人檔案看得到。
     */
    @Column(length = 100)
    private String name;

    @Column(length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** 僅 ORG_MEMBER 會有值——資料庫的 ck_users_org_membership 會強制這點。 */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false)
    private boolean disabled;

    /**
     * 認領者違規被停權的時間；沒有停權為 null。受限登入：仍可登入並看到停權通知，
     * 但所有寫入動作被擋（見 SuspendedUserInterceptor）。和 {@link #disabled}（完全拒絕登入）不同。
     */
    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspended_reason", columnDefinition = "text")
    private String suspendedReason;

    @Column(name = "suspended_by")
    private UUID suspendedBy;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** 看完首次登入說明的時間；null 表示還沒看過。 */
    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    /** 密碼雜湊（BCrypt）。null 代表還沒設定密碼（例如開發模式建立的帳號）。 */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /**
     * 信箱通過驗證的時間；null 表示還沒驗證。
     *
     * <p>未驗證的帳號只有一般民眾的權限（見 {@code AppPrincipal#effectiveRole}）。
     */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    /** 連續輸錯密碼被暫時鎖定到這個時間為止；沒被鎖定為 null。 */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    protected User() {
        // JPA
    }

    private User(String email, String displayName, UserRole role) {
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.disabled = false;
    }

    /** 註冊時建立帳號，一律先給最低權限的 DONOR；信箱要驗證過才算數。 */
    public static User newDonor(String email, String displayName) {
        return new User(email, displayName, UserRole.DONOR);
    }

    public static User newAdmin(String email, String displayName) {
        return new User(email, displayName, UserRole.ADMIN);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** 驗證成功。重複呼叫不改變第一次的時間。 */
    public void markEmailVerified(Instant at) {
        if (emailVerifiedAt == null) {
            this.emailVerifiedAt = at;
        }
    }

    /** 只給開發模式與測試把帳號切回「未驗證」。 */
    public void markEmailUnverified() {
        this.emailVerifiedAt = null;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void changePassword(String passwordHash, Instant at) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = at;
        clearLoginFailures();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    /**
     * 記一次登入失敗。連續失敗達到上限就鎖定一段時間，並把計數歸零（鎖定期滿後重新開始算）。
     *
     * @return 這次失敗是否觸發了鎖定
     */
    public boolean recordFailedLogin(Instant now, int maxFailures, java.time.Duration lockDuration) {
        this.failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            this.lockedUntil = now.plus(lockDuration);
            this.failedLoginCount = 0;
            return true;
        }
        return false;
    }

    public void clearLoginFailures() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    /** 設定檔白名單內的 email 於登入時取得管理員權限。 */
    public void promoteToAdmin() {
        this.role = UserRole.ADMIN;
        this.organizationId = null;
    }

    /** 註冊機構後，本人成為該機構的成員。 */
    public void joinOrganization(UUID organizationId) {
        this.role = UserRole.ORG_MEMBER;
        this.organizationId = organizationId;
    }

    /** 看完首次登入說明。重複呼叫不改變第一次的時間。 */
    public void markOnboarded(Instant at) {
        if (onboardedAt == null) {
            this.onboardedAt = at;
        }
    }

    public boolean isOnboarded() {
        return onboardedAt != null;
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    /** 使用者於個人檔案頁自行維護的資料。 */
    public void updateProfile(String displayName, String name, String phone) {
        this.displayName = displayName;
        this.name = name == null || name.isBlank() ? null : name.trim();
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public UserRole getRole() {
        return role;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void suspend(UUID adminId, String reason) {
        this.suspendedAt = Instant.now();
        this.suspendedReason = reason;
        this.suspendedBy = adminId;
    }

    public void reinstate() {
        this.suspendedAt = null;
        this.suspendedReason = null;
        this.suspendedBy = null;
    }

    public boolean isSuspended() {
        return suspendedAt != null;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public String getSuspendedReason() {
        return suspendedReason;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
