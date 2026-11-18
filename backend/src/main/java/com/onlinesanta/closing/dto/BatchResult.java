package com.onlinesanta.closing.dto;

/**
 * 批次動作的結果。
 *
 * @param done    成功處理的張數（補開的收據數，或補產成功的 PDF 數）
 * @param pending 仍待補或失敗的張數（例如檔期還沒有許可文號、版面放不下）
 */
public record BatchResult(int done, int pending) {
}
