package com.onlinesanta.attachment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.attachment.dto.UploadUrlResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 圖片上傳。
 *
 * <p>三步驟流程：索取上傳網址 → 前端直接 PUT 到儲存端 → 回頭確認。
 * 檔案本身不經過這個服務，省下 Cloud Run 的運算時間，也不受請求逾時限制。
 */
@RestController
@RequestMapping("/api")
@Tag(name = "附件", description = "圖片上傳與讀取")
public class AttachmentController {

    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @PostMapping("/uploads/signed-url")
    @Operation(summary = "索取上傳網址",
            description = "回傳限時的直傳網址。上傳時必須帶上回應中的 Content-Type，它已綁進簽章")
    public UploadUrlResponse createUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        return attachments.createUploadUrl(request);
    }

    @PostMapping("/attachments/{id}/confirm")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "確認上傳完成",
            description = "後端會向儲存端查證檔案確實存在，並核對實際的型別與大小")
    public AttachmentView confirm(@PathVariable UUID id) {
        return attachments.confirm(id);
    }
}
