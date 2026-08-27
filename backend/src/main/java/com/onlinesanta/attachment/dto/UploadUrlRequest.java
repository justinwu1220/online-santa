package com.onlinesanta.attachment.dto;

import java.util.UUID;

import com.onlinesanta.attachment.AttachmentPurpose;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 索取上傳網址。
 *
 * @param purpose  決定檔案存到哪個 bucket、由誰上傳、誰能看
 * @param targetId WISH_IMAGE 為願望 id，其餘為認領 id
 * @param sizeBytes 前端宣稱的檔案大小。用於提前擋下過大的檔案，
 *                  但不採信——確認階段會向儲存端查證實際大小
 */
public record UploadUrlRequest(
        @NotNull(message = "請指定用途")
        AttachmentPurpose purpose,

        @NotNull(message = "請指定目標資源")
        UUID targetId,

        @NotBlank(message = "請提供檔案型別")
        String contentType,

        @Positive(message = "檔案大小必須大於 0")
        long sizeBytes) {
}
