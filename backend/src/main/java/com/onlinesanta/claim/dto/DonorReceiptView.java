package com.onlinesanta.claim.dto;

import java.util.List;

import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.receipt.ClaimReceiptService.ReceiptData;

/**
 * 認領者本人看自己送出的捐贈收據資料，<strong>只有 {@code GET /api/claims/{id}/receipt} 回傳</strong>，
 * 而且只給認領者本人（機構與其他人一律 404）。
 *
 * <p>身分證字號／統一編號<strong>已遮罩</strong>（前 2 碼與後 2 碼），明文只有管理端看得到——
 * 見 docs/PRIVACY.md「捐贈收據資料」。
 */
public record DonorReceiptView(
        String name,
        String maskedIdNumber,
        String email,
        String address,
        List<AttachmentView> proofs) {

    public static DonorReceiptView of(ReceiptData masked, List<AttachmentView> proofs) {
        return new DonorReceiptView(
                masked.name(), masked.idNumber(), masked.email(), masked.address(), proofs);
    }
}
