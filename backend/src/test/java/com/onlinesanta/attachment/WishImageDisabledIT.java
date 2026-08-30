package com.onlinesanta.attachment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

/**
 * 願望示意圖關閉時的行為。
 *
 * <p>其餘的附件測試都在 {@link AttachmentApiIT}，那裡示意圖是開啟的。這一支刻意
 * 獨立出來，因為它要的是相反的設定——關閉時公開 bucket 根本不存在，端點必須自己
 * 擋下來，而不是等到寫入儲存端才失敗。
 */
@DisplayName("願望示意圖關閉時")
@TestPropertySource(properties = "app.storage.wish-image-enabled=false")
class WishImageDisabledIT extends ApiIntegrationTest {

    private static final String ORG_USER = "org@example.org";
    private static final String DONOR = "donor@example.com";
    private static final String JPEG = "image/jpeg";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.register("送禮之家", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);

        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);

        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    private Wish newWish() {
        return Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                "彩色鉛筆", "描述", WishCategory.ART, PriceRange.UNDER_500);
    }

    @Test
    @DisplayName("機構索取示意圖的上傳網址會被擋下")
    void rejectsWishImageUpload() throws Exception {
        UUID wishId = wishes.save(newWish()).getId();
        var request = new UploadUrlRequest(AttachmentPurpose.WISH_IMAGE, wishId, JPEG, 120_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("WISH_IMAGE_DISABLED"));
    }

    @Test
    @DisplayName("寄送證明不受影響")
    void allowsShippingProof() throws Exception {
        Wish wish = newWish();
        wish.publish();
        UUID wishId = wishes.save(wish).getId();

        String claim = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID claimId = UUID.fromString(json.readTree(claim).get("id").asText());

        var request = new UploadUrlRequest(AttachmentPurpose.SHIPPING_PROOF, claimId, JPEG, 120_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), DONOR))
                .andExpect(status().isOk());
    }
}
