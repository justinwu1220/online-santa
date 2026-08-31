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
 * 由 Firebase Authentication 對應而來的本地帳號。
 *
 * <p>授權判斷一律以本表為準，而非 Firebase custom claims：claims 要等 ID token 刷新
 * （約一小時）才生效，會讓「Admin 剛核准的機構」在那段期間無法操作。
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "firebase_uid", nullable = false, length = 128)
    private String firebaseUid;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", length = 100)
    private String displayName;

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

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
        // JPA
    }

    private User(String firebaseUid, String email, String displayName, UserRole role) {
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.disabled = false;
    }

    /** 首次登入時建立帳號，一律先給最低權限的 DONOR。 */
    public static User newDonor(String firebaseUid, String email, String displayName) {
        return new User(firebaseUid, email, displayName, UserRole.DONOR);
    }

    public static User newAdmin(String firebaseUid, String email, String displayName) {
        return new User(firebaseUid, email, displayName, UserRole.ADMIN);
    }

    /**
     * 改綁 Firebase uid。
     *
     * <p>用於 Firebase 端帳號被刪除後、以同一信箱重新註冊的情況：本地的認領紀錄
     * 應該延續給同一個人，而不是產生一個孤立的新帳號。
     */
    public void linkFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
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

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    /** 使用者於個人檔案頁自行維護的資料。 */
    public void updateProfile(String displayName, String phone) {
        this.displayName = displayName;
        this.phone = phone;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
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

    public boolean isDisabled() {
        return disabled;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
