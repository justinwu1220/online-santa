package com.onlinesanta.admin.dto;

/**
 * 解除凍結與核准解除申請時的選項。
 *
 * @param restoreLetter 信件照片目前被屏蔽時，是否一併恢復顯示（預設不恢復：願望回到牆上但沒有信件）
 */
public record UnfreezeRequest(Boolean restoreLetter) {

    public boolean shouldRestoreLetter() {
        return Boolean.TRUE.equals(restoreLetter);
    }
}
