package com.onlinesanta.attachment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditService;
import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.attachment.dto.UploadUrlResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 圖片上傳與刪除。
 *
 * <p>上傳是三步驟流程：索取上傳網址 → 前端直接 PUT 到儲存端 → 回頭確認。
 * 檔案本身不經過這個服務，省下 Cloud Run 的運算時間，也不受請求逾時限制。
 */
@RestController
@RequestMapping("/api")
@Tag(name = "附件", description = "圖片上傳與讀取")
public class AttachmentController {

    private final AttachmentService attachments;
    private final AdminAuditService audit;

    public AttachmentController(AttachmentService attachments, AdminAuditService audit) {
        this.attachments = attachments;
        this.audit = audit;
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

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "刪除附件",
            description = "寄送證明限該筆認領的捐贈者本人、回饋照片限願望所屬機構，兩者管理員都能刪；"
                    + "機構文件（ORG_DOCUMENT）機構自己不能刪，只有管理員能刪——審核有誤時由管理員"
                    + "刪掉有問題的文件，機構後台既有的上傳功能名額就會自動空出來。管理員的刪除"
                    + "一律寫入稽核紀錄")
    public void delete(@PathVariable UUID id) {
        AttachmentDeletionResult result = attachments.delete(id);
        if (!result.deletedByAdmin()) {
            return;
        }
        if (result.purpose() == AttachmentPurpose.ORG_DOCUMENT) {
            audit.record(AdminAuditAction.DELETE_ORGANIZATION_DOCUMENT, result.ownerId(),
                    "刪除機構文件 %s".formatted(result.attachmentId()));
        } else {
            audit.record(AdminAuditAction.DELETE_ATTACHMENT, result.ownerId(),
                    "刪除附件 %s（%s），所屬認領 %s".formatted(
                            result.attachmentId(), result.purpose(), result.ownerId()));
        }
    }
}
