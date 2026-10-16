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
 * <p>寫入由 {@code ClaimService.ship} 呼叫（與回報寄送同一個交易）；讀取只有管理端點，
 * 呼叫端必須自己確認管理員身分並寫稽核紀錄。
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

    /** 解密後的收據資料。只在管理端的回應裡出現。 */
    public record ReceiptData(String name, String idNumber, String email, String address) {
    }
}
