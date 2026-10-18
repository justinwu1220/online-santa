package com.onlinesanta.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.attachment.UploadStatus;
import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.organization.dto.OrganizationRegistrationRequest;
import com.onlinesanta.organization.dto.OrganizationUpdateRequest;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;

class OrganizationApiIT extends ApiIntegrationTest {

    private static final String ORG_USER = "org@example.org";

    @Autowired
    UserRepository users;

    private OrganizationRegistrationRequest registration(String name) {
        return new OrganizationRegistrationRequest(
                name, "王承辦", "contact@example.org", "02-1234-5678", "0912-345-678",
                "台北市中正區某路 1 號", "平日上午9:00-下午5:00", "服務失依兒童的機構");
    }

    @Test
    void registersOrganizationAsPendingAndPromotesRegistrant() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("陽光之家")), ORG_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("陽光之家"))
                // 承辦人存在機構上，不是 users.display_name——後者沒有任何地方讀得到
                .andExpect(jsonPath("$.contactPerson").value("王承辦"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.canPublishWishes").value(false))
                // 待審核就能存草稿，只是不能公開——後台靠這兩個旗標決定按鈕的狀態
                .andExpect(jsonPath("$.canDraftWishes").value(true))
                .andExpect(jsonPath("$.releasePolicy").value("MANUAL"))
                .andExpect(jsonPath("$.releaseAfterDays").doesNotExist());

        var registrant = users.findByEmailIgnoreCase(ORG_USER).orElseThrow();
        assertThat(registrant.getRole()).isEqualTo(UserRole.ORG_MEMBER);
        assertThat(registrant.getOrganizationId()).isNotNull();
    }

    @Test
    void rejectsAnonymousRegistration() throws Exception {
        mvc.perform(withBody(post("/api/organizations"), registration("無名氏之家")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsDuplicateOrganizationName() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("重複之家")), ORG_USER))
                .andExpect(status().isCreated());

        mvc.perform(as(withBody(post("/api/organizations"), registration("重複之家")),
                        "another@example.org"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NAME_TAKEN"));
    }

    @Test
    void rejectsSecondRegistrationBySameUser() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("第一家")), ORG_USER))
                .andExpect(status().isCreated());

        mvc.perform(as(withBody(post("/api/organizations"), registration("第二家")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_IN_ORGANIZATION"));
    }

    @Test
    void reportsFieldLevelValidationErrors() throws Exception {
        var invalid = new OrganizationRegistrationRequest(
                "", null, "not-an-email", null, null, null, null, null);

        mvc.perform(as(withBody(post("/api/organizations"), invalid), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.contactEmail").exists())
                // 承辦人是審核與聯繫的窗口，也必填
                .andExpect(jsonPath("$.fieldErrors.contactPerson").exists())
                // 行動電話必填（聯絡電話選填），地址同樣個別必填
                .andExpect(jsonPath("$.fieldErrors.mobilePhone").exists())
                .andExpect(jsonPath("$.fieldErrors.address").exists())
                // 方便收件時間同樣必填——捐贈者要靠它判斷什麼時候送比較方便
                .andExpect(jsonPath("$.fieldErrors.preferredDeliveryTime").exists());
    }

    @Test
    void rejectsBlankPhoneAndAddressOnUpdateToo() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("先建立起來")), ORG_USER))
                .andExpect(status().isCreated());

        // 只在註冊時擋是不夠的——機構之後在設定頁清空一樣會讓寄送地址消失；
        // 行動電話清空會被擋（聯絡電話則可以空）
        var cleared = new OrganizationUpdateRequest(
                "先建立起來", "王承辦", "contact@example.org", "   ", "", "",
                "平日上午9:00-下午5:00", "服務失依兒童", ReleasePolicy.MANUAL, null);

        mvc.perform(as(withBody(patch("/api/organizations/me"), cleared), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.mobilePhone").exists())
                .andExpect(jsonPath("$.fieldErrors.address").exists());
    }

    @Test
    void acceptsRegistrationWithOnlyMobilePhone() throws Exception {
        // 只有手機的小型機構是真實情境，必須過——聯絡電話是選填
        var mobileOnly = new OrganizationRegistrationRequest(
                "只有手機之家", "王承辦", "contact@example.org", null, "0912-345-678",
                "台北市中正區某路 1 號", "平日上午9:00-下午5:00", "服務失依兒童");

        mvc.perform(as(withBody(post("/api/organizations"), mobileOnly), ORG_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactPhone").doesNotExist())
                .andExpect(jsonPath("$.mobilePhone").value("0912-345-678"));
    }

    @Test
    void rejectsRegistrationWithoutMobilePhone() throws Exception {
        // 空白字串而不是 null——前端清空時送的就是 ''，不是 null。
        // 只填聯絡電話也不行：行動電話才是必填
        var landlineOnly = new OrganizationRegistrationRequest(
                "只有市話之家", "王承辦", "contact@example.org", "02-1234-5678", "",
                "台北市中正區某路 1 號", "平日上午9:00-下午5:00", "服務失依兒童");

        mvc.perform(as(withBody(post("/api/organizations"), landlineOnly), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.mobilePhone").exists());
    }

    @Test
    void mobilePhoneRoundTripsThroughRegistrationAndUpdate() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("雙電話之家")), ORG_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mobilePhone").value("0912-345-678"));

        mvc.perform(as(get("/api/organizations/me"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobilePhone").value("0912-345-678"));

        // 設定頁換一支新的手機號碼，也要能存得進去、讀得回來
        var update = new OrganizationUpdateRequest("雙電話之家", "王承辦", "contact@example.org",
                "02-1234-5678", "0987-654-321", "台北市中正區某路 1 號", "平日上午9:00-下午5:00",
                "服務失依兒童的機構", ReleasePolicy.MANUAL, null);

        mvc.perform(as(withBody(patch("/api/organizations/me"), update), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobilePhone").value("0987-654-321"));
    }

    @Test
    void acceptsUpdateClearingOneOfTheTwoPhones() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("只留手機之家")), ORG_USER))
                .andExpect(status().isCreated());

        // 設定頁把市話清空、只留手機——這是舊的 @NotBlank 會誤擋的那一半，
        // 改完之後必須真的放行
        var onlyMobile = new OrganizationUpdateRequest("只留手機之家", "王承辦", "contact@example.org",
                "", "0912-345-678", "台北市中正區某路 1 號", "平日上午9:00-下午5:00",
                "服務失依兒童的機構", ReleasePolicy.MANUAL, null);

        // updateProfile 是直接賦值，清空後存的是空字串而不是 null（跟一開始註冊時
        // 傳 null 不同）；前端的 && 判斷與 Row 的 value || '—' 都拿空字串當「沒有」，
        // 所以這裡斷言空字串，不是 doesNotExist()
        mvc.perform(as(withBody(patch("/api/organizations/me"), onlyMobile), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactPhone").value(""))
                .andExpect(jsonPath("$.mobilePhone").value("0912-345-678"));
    }

    @Test
    void rejectsUpdateWithBlankMobilePhone() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("兩支都清空之家")), ORG_USER))
                .andExpect(status().isCreated());

        // 只在註冊時擋不夠：機構在設定頁把行動電話清空，平台與捐贈者就少了最穩定的聯絡方式
        var bothBlank = new OrganizationUpdateRequest("兩支都清空之家", "王承辦", "contact@example.org",
                "", "", "台北市中正區某路 1 號", "平日上午9:00-下午5:00",
                "服務失依兒童的機構", ReleasePolicy.MANUAL, null);

        mvc.perform(as(withBody(patch("/api/organizations/me"), bothBlank), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.mobilePhone").exists());
    }

    @Test
    void rejectsMobilePhoneLongerThanLimit() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("手機太長之家")), ORG_USER))
                .andExpect(status().isCreated());

        // 新欄位也要有長度上限，而且錯誤要掛在自己的 key 上，不是跟著 contactPhone 走
        var tooLong = new OrganizationUpdateRequest("手機太長之家", "王承辦", "contact@example.org",
                "02-1234-5678", "0".repeat(41), "台北市中正區某路 1 號", "平日上午9:00-下午5:00",
                "服務失依兒童的機構", ReleasePolicy.MANUAL, null);

        mvc.perform(as(withBody(patch("/api/organizations/me"), tooLong), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.mobilePhone").exists());
    }

    @Test
    void preferredDeliveryTimeRoundTripsThroughRegistrationAndUpdate() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("收件時段之家")), ORG_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferredDeliveryTime").value("平日上午9:00-下午5:00"));

        mvc.perform(as(get("/api/organizations/me"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredDeliveryTime").value("平日上午9:00-下午5:00"));

        // 機構在設定頁改一段新的時段說明，也要能存得進去、讀得回來
        var update = new OrganizationUpdateRequest("收件時段之家", "王承辦", "contact@example.org",
                "02-1234-5678", "0912-345-678", "台北市中正區某路 1 號", "假日全天皆可",
                "服務失依兒童的機構", ReleasePolicy.MANUAL, null);

        mvc.perform(as(withBody(patch("/api/organizations/me"), update), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredDeliveryTime").value("假日全天皆可"));
    }

    @Test
    void rejectsBlankPreferredDeliveryTimeOnUpdate() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("清空收件時段之家")), ORG_USER))
                .andExpect(status().isCreated());

        // 跟電話/地址一樣：設定頁清空方便收件時間要被擋下，不能靜默存成空字串
        var cleared = new OrganizationUpdateRequest("清空收件時段之家", "王承辦", "contact@example.org",
                "02-1234-5678", "0912-345-678", "台北市中正區某路 1 號", "",
                "服務失依兒童的機構", ReleasePolicy.MANUAL, null);

        mvc.perform(as(withBody(patch("/api/organizations/me"), cleared), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.preferredDeliveryTime").exists());
    }

    @Test
    void deniesOrganizationEndpointsToNonMembers() throws Exception {
        mvc.perform(as(get("/api/organizations/me"), "donor@example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ORG_MEMBER"));
    }

    @Test
    void updatesReleasePolicyToAutomatic() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("自動釋回之家")), ORG_USER))
                .andExpect(status().isCreated());

        var update = new OrganizationUpdateRequest(
                "自動釋回之家", "王承辦", "new@example.org", "02-0000-0000", "0912-345-678",
                "新地址", "平日上午9:00-下午5:00", "新簡介", ReleasePolicy.AUTO, 10);

        mvc.perform(as(withBody(patch("/api/organizations/me"), update), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasePolicy").value("AUTO"))
                .andExpect(jsonPath("$.releaseAfterDays").value(10))
                .andExpect(jsonPath("$.contactEmail").value("new@example.org"));
    }

    @Test
    void clearsGracePeriodWhenSwitchingBackToManual() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("切回手動之家")), ORG_USER))
                .andExpect(status().isCreated());

        var toAuto = new OrganizationUpdateRequest("切回手動之家", "王承辦", "a@example.org",
                "02-1234-5678", "0912-345-678", "台北市中正區某路 1 號", "平日上午9:00-下午5:00",
                "服務失依兒童", ReleasePolicy.AUTO, 5);
        mvc.perform(as(withBody(patch("/api/organizations/me"), toAuto), ORG_USER))
                .andExpect(status().isOk());

        // 切回 MANUAL 時必須清掉天數，否則違反 ck_org_release_after_days
        var toManual = new OrganizationUpdateRequest("切回手動之家", "王承辦", "a@example.org",
                "02-1234-5678", "0912-345-678", "台北市中正區某路 1 號", "平日上午9:00-下午5:00",
                "服務失依兒童", ReleasePolicy.MANUAL, 5);
        mvc.perform(as(withBody(patch("/api/organizations/me"), toManual), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasePolicy").value("MANUAL"))
                .andExpect(jsonPath("$.releaseAfterDays").doesNotExist());
    }

    @Test
    void rejectsAutomaticPolicyWithoutGracePeriod() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("缺天數之家")), ORG_USER))
                .andExpect(status().isCreated());

        var invalid = new OrganizationUpdateRequest("缺天數之家", "王承辦", "a@example.org",
                "02-1234-5678", "0912-345-678", "台北市中正區某路 1 號", "平日上午9:00-下午5:00",
                "服務失依兒童", ReleasePolicy.AUTO, null);

        mvc.perform(as(withBody(patch("/api/organizations/me"), invalid), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RELEASE_POLICY"));
    }

    // ------------------------------------------------------------ 1018 新版：機構欄位

    @Test
    void phonesAreStoredWithDashesAndInvalidOnesAreRejected() throws Exception {
        var request = new OrganizationRegistrationRequest(
                "電話整理之家", "王承辦", "contact@example.org", "(04) 2234 5678", "0912345678",
                "台北市中正區某路 1 號", "平日上午9:00-下午5:00", "服務失依兒童", null);
        mvc.perform(as(withBody(post("/api/organizations"), request), ORG_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactPhone").value("04-2234-5678"))
                .andExpect(jsonPath("$.mobilePhone").value("0912-345-678"));

        var bad = new OrganizationUpdateRequest("電話整理之家", "王承辦", "contact@example.org",
                "02-2234-56789", "0812-345-678", "台北市中正區某路 1 號", "平日上午9:00-下午5:00",
                "服務失依兒童", ReleasePolicy.MANUAL, null);
        mvc.perform(as(withBody(patch("/api/organizations/me"), bad), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").exists())
                .andExpect(jsonPath("$.fieldErrors.mobilePhone").exists());
    }

    @Test
    void descriptionIsRequiredAndAtMostOneHundredCharacters() throws Exception {
        var blank = new OrganizationRegistrationRequest(
                "沒簡介之家", "王承辦", "contact@example.org", null, "0912-345-678",
                "台北市中正區某路 1 號", "平日上午9:00-下午5:00", "  ", null);
        mvc.perform(as(withBody(post("/api/organizations"), blank), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").exists());

        var tooLong = new OrganizationRegistrationRequest(
                "簡介太長之家", "王承辦", "contact@example.org", null, "0912-345-678",
                "台北市中正區某路 1 號", "平日上午9:00-下午5:00", "字".repeat(101), null);
        mvc.perform(as(withBody(post("/api/organizations"), tooLong), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").exists());

        var exactly = new OrganizationRegistrationRequest(
                "剛好一百字之家", "王承辦", "contact@example.org", null, "0912-345-678",
                "台北市中正區某路 1 號", "平日上午9:00-下午5:00", "字".repeat(100), null);
        mvc.perform(as(withBody(post("/api/organizations"), exactly), ORG_USER))
                .andExpect(status().isCreated());
    }

    @Test
    void websiteIsOptionalButMustBeAFullUrl() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations"), registration("沒官網之家")), ORG_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.website").doesNotExist());

        var bad = new OrganizationUpdateRequest("沒官網之家", "王承辦", "contact@example.org",
                "02-1234-5678", "0912-345-678", "台北市中正區某路 1 號", "平日上午9:00-下午5:00",
                "服務失依兒童", "www.example.org", ReleasePolicy.MANUAL, null);
        mvc.perform(as(withBody(patch("/api/organizations/me"), bad), ORG_USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.website").exists());

        var good = new OrganizationUpdateRequest("沒官網之家", "王承辦", "contact@example.org",
                "02-1234-5678", "0912-345-678", "台北市中正區某路 1 號", "平日上午9:00-下午5:00",
                "服務失依兒童", "https://www.example.org", ReleasePolicy.MANUAL, null);
        mvc.perform(as(withBody(patch("/api/organizations/me"), good), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.website").value("https://www.example.org"));
    }

    @Test
    void organizationPhotoOnlyAcceptsJpegOrPngAndReplacesThePreviousOne() throws Exception {
        String body = mvc.perform(as(withBody(post("/api/organizations"), registration("有照片之家")), ORG_USER))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID orgId = UUID.fromString(json.readTree(body).get("id").asText());

        mvc.perform(as(get("/api/organizations/me"), ORG_USER))
                .andExpect(jsonPath("$.photoUrl").doesNotExist());

        // WebP 被擋下（SPEC 13.4 只收 JPG／PNG）
        var webp = new UploadUrlRequest(AttachmentPurpose.ORG_PHOTO, orgId, "image/webp", 120_000);
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), webp), ORG_USER))
                .andExpect(status().is4xxClientError());

        UUID first = uploadConfirmedAttachment(AttachmentPurpose.ORG_PHOTO, orgId, ORG_USER, "image/png");
        mvc.perform(as(get("/api/organizations/me"), ORG_USER))
                .andExpect(jsonPath("$.photoUrl").exists());

        // 再傳一張：舊的被取代，只剩一張
        uploadConfirmedAttachment(AttachmentPurpose.ORG_PHOTO, orgId, ORG_USER, "image/jpeg");
        assertThat(attachmentRepository.findByPurposeAndOwnerIdAndUploadStatusOrderByCreatedAtAsc(
                AttachmentPurpose.ORG_PHOTO, orgId, UploadStatus.CONFIRMED)).hasSize(1);

        // 不支援刪除，只能換新（舊的那張已被取代，所以找不到；對新的那張刪除也被擋）
        UUID current = attachmentRepository.findByPurposeAndOwnerIdAndUploadStatusOrderByCreatedAtAsc(
                AttachmentPurpose.ORG_PHOTO, orgId, UploadStatus.CONFIRMED).get(0).getId();
        mvc.perform(as(delete("/api/attachments/{id}", current), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ATTACHMENT_NOT_DELETABLE"));
        assertThat(first).isNotEqualTo(current);
    }
}
