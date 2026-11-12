package com.onlinesanta.changelog;

import java.util.Map;
import java.util.Objects;

import com.onlinesanta.common.FieldDiff;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.user.User;

/**
 * 把實體攤平成「欄位 → 值」的快照，給 {@link ProfileChangeLogService} 比對。
 *
 * <p>鍵是機器欄位名，與 API 的欄位名一致，中文標籤由前端負責。
 */
public final class ProfileSnapshots {

    private ProfileSnapshots() {
    }

    /**
     * 認領者帳號上會變動、且值得留證的欄位。
     *
     * <p>包含 role 與 organizationId：它們不是個資，但 {@code promoteToAdmin} 目前完全
     * 不留痕跡，而「這個帳號什麼時候變成管理員的」在爭議裡是要回答的問題。
     *
     * <p>不包含 lastLoginAt／onboardedAt（不是個資，而且每次登入都變），也不包含停權
     * 三欄——那條軌跡由 suspension_records 與 admin_audit_logs 擁有，記兩次只會產生
     * 兩份可能互相矛盾的紀錄。email 沒有 setter，不會變。
     */
    public static Map<String, String> of(User user) {
        Map<String, String> snapshot = FieldDiff.snapshot();
        snapshot.put("displayName", user.getDisplayName());
        snapshot.put("name", user.getName());
        snapshot.put("phone", user.getPhone());
        snapshot.put("firebaseUid", maskUid(user.getFirebaseUid()));
        snapshot.put("role", Objects.toString(user.getRole(), null));
        snapshot.put("organizationId", Objects.toString(user.getOrganizationId(), null));
        return snapshot;
    }

    /**
     * 機構可編輯的 13 個欄位。
     *
     * <p>比舊的 organization_revisions 多了 releasePolicy 與 releaseAfterDays：它們不是
     * 個資，但 updateMine 每次都會寫，而且會改變認領是否被自動收回——「系統把我的認領
     * 收回去了，但沒有人改過設定」正是這張表要回答的爭議。
     *
     * <p>不包含 status／reviewNote／reviewedBy／reviewedAt：那些是管理員的決定，已經由
     * APPROVE／REJECT／SUSPEND／REACTIVATE_ORGANIZATION 的稽核紀錄涵蓋。
     */
    public static Map<String, String> of(Organization organization) {
        Map<String, String> snapshot = FieldDiff.snapshot();
        snapshot.put("name", organization.getName());
        snapshot.put("contactPerson", organization.getContactPerson());
        snapshot.put("responsiblePerson", organization.getResponsiblePerson());
        snapshot.put("withholdingTaxId", organization.getWithholdingTaxId());
        snapshot.put("contactEmail", organization.getContactEmail());
        snapshot.put("contactPhone", organization.getContactPhone());
        snapshot.put("mobilePhone", organization.getMobilePhone());
        snapshot.put("address", organization.getAddress());
        snapshot.put("preferredDeliveryTime", organization.getPreferredDeliveryTime());
        snapshot.put("description", organization.getDescription());
        snapshot.put("website", organization.getWebsite());
        snapshot.put("releasePolicy", Objects.toString(organization.getReleasePolicy(), null));
        snapshot.put("releaseAfterDays", Objects.toString(organization.getReleaseAfterDays(), null));
        return snapshot;
    }

    /**
     * Firebase uid 只留前 8 碼。
     *
     * <p>它是憑證性質的識別碼：記下「換過」這件事對舉證有用，完整的值沒有加分，只是
     * 把它多複製一份到另一張表。
     */
    private static String maskUid(String uid) {
        if (uid == null || uid.length() <= 8) {
            return uid;
        }
        return uid.substring(0, 8) + "…";
    }
}
