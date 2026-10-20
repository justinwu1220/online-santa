package com.onlinesanta.admin.dto;

import java.util.List;

import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.receipt.ClaimReceiptService.ReceiptData;

/**
 * 捐贈收據資料，<strong>只有管理端專用的端點回傳</strong>。
 *
 * <p>身分證字號／統一編號在這裡是解密後的明文。機構端與捐贈者端的一般回應型別都沒有這些
 * 欄位，也不該為了方便加進去（認領者本人看自己的資料用 {@code DonorReceiptView}，證號已遮罩）
 * ——見 docs/PRIVACY.md「捐贈收據資料」。
 */
public record ClaimReceiptView(
        String name,
        String idNumber,
        String email,
        String address,
        List<AttachmentView> proofs) {

    public static ClaimReceiptView of(ReceiptData data, List<AttachmentView> proofs) {
        return new ClaimReceiptView(
                data.name(), data.idNumber(), data.email(), data.address(), proofs);
    }
}
