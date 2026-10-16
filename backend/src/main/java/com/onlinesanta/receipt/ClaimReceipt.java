package com.onlinesanta.receipt;

import java.util.UUID;

import com.onlinesanta.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 認領者申請的捐贈收據資料，與認領一對一。
 *
 * <p>刻意不是 {@code Claim} 的欄位或關聯：機構端與捐贈者端的回應型別都從 {@code Claim}
 * 轉出，資料不在那個實體上，就不可能被誤帶進那些回應。只有管理端專用的端點讀這張表。
 *
 * <p>身分證字號／統一編號只存密文，這個實體不碰明文；加解密在 {@link ClaimReceiptService}。
 */
@Entity
@Table(name = "claim_receipts")
public class ClaimReceipt extends BaseEntity {

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "id_number_encrypted", nullable = false, length = 255)
    private String idNumberEncrypted;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String address;

    protected ClaimReceipt() {
        // JPA
    }

    public ClaimReceipt(UUID claimId, String name, String idNumberEncrypted,
                        String email, String address) {
        this.claimId = claimId;
        this.name = name;
        this.idNumberEncrypted = idNumberEncrypted;
        this.email = email;
        this.address = address;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public String getName() {
        return name;
    }

    public String getIdNumberEncrypted() {
        return idNumberEncrypted;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }
}
