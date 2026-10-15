package com.onlinesanta.attachment;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.common.exception.ForbiddenException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageProperties;
import com.onlinesanta.user.UserRole;
import com.onlinesanta.wish.WishRepository;

/**
 * {@link AttachmentService#createUploadUrl} 裡 requirePurposeEnabled 這道檢查。
 *
 * <p>用單元測試而非整合測試：既有的 {@code OrgDocumentAndWishLetterAttachmentIT}
 * 跑在 Spring 整合測試環境，{@code application-test.yml} 的
 * {@code app.storage.public-bucket} 本來就不是空字串，沒辦法在那裡重現「公開
 * bucket 沒設定」這個情境（正是正式環境踩到的那個 bug）。這裡只關心
 * {@code requirePurposeEnabled} 要不要擋下來，不需要把整條上傳流程跑到底，所以
 * 其餘 repository／storage 都是不會被用到的 mock——預設的 {@code Optional} 回應
 * 已經是 {@code Optional.empty()}，剛好拿來證明「有沒有被擋下」：如果檢查放行了，
 * 下一步的授權查詢會因為查無資料丟 {@link ResourceNotFoundException}；如果被新檢查
 * 擋下，會在那之前就丟 {@link ForbiddenException}。
 */
class AttachmentServiceTest {

    private static final AppPrincipal PRINCIPAL = new AppPrincipal(
            UUID.randomUUID(), "org@example.org", UserRole.ORG_MEMBER, UUID.randomUUID(), true);

    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private AttachmentService serviceWith(String publicBucket, boolean wishImageEnabled) {
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.require()).thenReturn(PRINCIPAL);

        StorageProperties properties = new StorageProperties(
                publicBucket, wishImageEnabled, "test-private",
                Duration.ofMinutes(15), Duration.ofMinutes(10), 5_242_880L, ALLOWED_TYPES, null);

        return new AttachmentService(
                mock(AttachmentRepository.class),
                mock(WishRepository.class),
                mock(ClaimRepository.class),
                mock(ObjectStorage.class),
                properties,
                currentUser,
                mock(ApplicationEventPublisher.class));
    }

    private static UploadUrlRequest requestFor(AttachmentPurpose purpose) {
        return new UploadUrlRequest(purpose, UUID.randomUUID(), "image/jpeg", 1_000);
    }

    @Test
    @DisplayName("公開 bucket 名稱空白時，連沒有自己開關保護的 WISH_LETTER 也會被擋下")
    void blocksWishLetterWhenPublicBucketIsBlank() {
        AttachmentService service = serviceWith("", true);

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.createUploadUrl(requestFor(AttachmentPurpose.WISH_LETTER)))
                .extracting(ForbiddenException::getErrorCode)
                .isEqualTo("PUBLIC_BUCKET_NOT_CONFIGURED");
    }

    @Test
    @DisplayName("公開 bucket 有設定時，WISH_LETTER 會繼續往下走到授權檢查，不會被新檢查擋下")
    void allowsWishLetterWhenPublicBucketIsConfigured() {
        AttachmentService service = serviceWith("online-santa-public", true);

        assertThatThrownBy(() -> service.createUploadUrl(requestFor(AttachmentPurpose.WISH_LETTER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("WISH_IMAGE 關閉時，即使公開 bucket 也沒設定，看到的仍是語意更精確的 WISH_IMAGE_DISABLED")
    void wishImageDisabledTakesPriorityOverMissingBucket() {
        AttachmentService service = serviceWith("", false);

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.createUploadUrl(requestFor(AttachmentPurpose.WISH_IMAGE)))
                .extracting(ForbiddenException::getErrorCode)
                .isEqualTo("WISH_IMAGE_DISABLED");
    }

    @Test
    @DisplayName("WISH_IMAGE 開了，但公開 bucket 還沒設定時，第二道防線擋下來")
    void blocksWishImageWhenEnabledButBucketStillNotConfigured() {
        AttachmentService service = serviceWith("", true);

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> service.createUploadUrl(requestFor(AttachmentPurpose.WISH_IMAGE)))
                .extracting(ForbiddenException::getErrorCode)
                .isEqualTo("PUBLIC_BUCKET_NOT_CONFIGURED");
    }

    @Test
    @DisplayName("私密 bucket 的用途（寄送證明）不受這道新檢查影響，即使公開 bucket 沒設定")
    void doesNotAffectPrivateBucketPurposes() {
        AttachmentService service = serviceWith("", true);

        assertThatThrownBy(() -> service.createUploadUrl(requestFor(AttachmentPurpose.SHIPPING_PROOF)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
