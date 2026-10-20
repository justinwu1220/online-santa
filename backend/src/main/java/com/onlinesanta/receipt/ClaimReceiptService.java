package com.onlinesanta.receipt;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.claim.dto.ReceiptRequest;
import com.onlinesanta.common.exception.ResourceNotFoundException;

/**
 * 捐贈收據資料的讀寫。身分證字號／統一編號在這裡加密、解密，其他層碰不到明文。
 *
 * <p>寫入由 {@code ClaimService.ship} 呼叫（與回報寄送同一個交易）；讀取有兩條路：
 * 管理端點讀明文（呼叫端必須自己確認管理員身分並寫稽核紀錄），認領者本人讀遮罩後的版本。
 */
@Service
public class ClaimReceiptService {

    private final ClaimReceiptRepository receipts;
    private final ReceiptCrypto crypto;

    public ClaimReceiptService(ClaimReceiptRepository receipts, ReceiptCrypto crypto) {
        this.receipts = receipts;
        this.crypto = crypto;
    }

    @Transactional
    public void save(UUID claimId, ReceiptRequest request) {
        // 英文字母一律存大寫，管理員核對時不必猜大小寫
        String idNumber = request.idNumber().trim().toUpperCase(Locale.ROOT);
        receipts.save(new ClaimReceipt(
                claimId,
                request.name().trim(),
                crypto.encrypt(idNumber),
                request.email().trim(),
                request.address().trim()));
    }

    /** 管理端讀取：回傳解密後的資料。沒有申請就 404。 */
    @Transactional(readOnly = true)
    public ReceiptData read(UUID claimId) {
        ClaimReceipt receipt = receipts.findByClaimId(claimId)
                .orElseThrow(() -> ResourceNotFoundException.of("捐贈收據", claimId));
        return new ReceiptData(receipt.getName(), crypto.decrypt(receipt.getIdNumberEncrypted()),
                receipt.getEmail(), receipt.getAddress());
    }

    /**
     * 認領者本人讀取：證號遮罩後才回傳（前 2 碼與後 2 碼，中間以 * 取代），明文不離開這一層。
     * 呼叫端必須自己確認呼叫者就是這筆認領的認領者。沒有申請就 404。
     */
    @Transactional(readOnly = true)
    public ReceiptData readMasked(UUID claimId) {
        ReceiptData data = read(claimId);
        return new ReceiptData(data.name(), mask(data.idNumber()), data.email(), data.address());
    }

    static String mask(String idNumber) {
        if (idNumber.length() <= 4) {
            return "*".repeat(idNumber.length());
        }
        return idNumber.substring(0, 2)
                + "*".repeat(idNumber.length() - 4)
                + idNumber.substring(idNumber.length() - 2);
    }

    /** 收據資料。證號是明文（管理端）或已遮罩（認領者本人），看是哪個方法回傳的。 */
    public record ReceiptData(String name, String idNumber, String email, String address) {
    }
}
