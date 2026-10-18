package com.onlinesanta.terms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

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

/** 認領者須知的同意紀錄：版本化、只增不改，以及認領前的檢查（這裡把檢查打開）。 */
@DisplayName("參與須知的同意")
@TestPropertySource(properties = {
        "app.terms.enforce-on-claim=true",
        "app.terms.enforce-on-organization-register=true"})
class TermsApiIT extends ApiIntegrationTest {

    private static final String CURRENT = "v6（2026-10-16）";
    private static final String ORG_CURRENT = "v7（2026-10-17）";
    private static final String DONOR = "terms-donor@example.com";
    private static final String OTHER = "terms-other@example.com";

    @Autowired
    UserRepository users;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    WishRepository wishes;

    @Autowired
    TermsConsentRepository consents;

    private UUID donorId;

    @BeforeEach
    void setUp() {
        donorId = users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾")).getId();
        users.save(User.newDonor(TestJwtSupport.uidFor(OTHER), OTHER, "另一位"));
    }

    private Map<String, String> body(String version) {
        return Map.of("version", version);
    }

    @Test
    @DisplayName("沒同意過：需要同意，目前版本照設定回傳")
    void freshUserNeedsToAccept() throws Exception {
        mvc.perform(as(get("/api/me/terms"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("DONOR"))
                .andExpect(jsonPath("$[0].currentVersion").value(CURRENT))
                .andExpect(jsonPath("$[0].acceptedVersion").doesNotExist())
                .andExpect(jsonPath("$[0].upToDate").value(false));
    }

    @Test
    @DisplayName("同意目前版本後 upToDate，重複同意不會多出一列")
    void acceptingCurrentVersionIsIdempotent() throws Exception {
        mvc.perform(as(withBody(post("/api/me/terms/DONOR/accept"), body(CURRENT)), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upToDate").value(true))
                .andExpect(jsonPath("$.acceptedVersion").value(CURRENT))
                // 前端用 new Date() 解析來顯示「你已於…同意」：必須是 ISO 字串，不能是數字時間戳
                .andExpect(jsonPath("$.acceptedAt").value(
                        org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")));

        mvc.perform(as(withBody(post("/api/me/terms/DONOR/accept"), body(CURRENT)), DONOR))
                .andExpect(status().isOk());

        assertThat(consents.countByUserIdAndTermsType(donorId, TermsType.DONOR)).isEqualTo(1);
    }

    @Test
    @DisplayName("同意的版本不是目前版本：拒絕，不留紀錄")
    void rejectsAcceptingAVersionThatIsNotCurrent() throws Exception {
        mvc.perform(as(withBody(post("/api/me/terms/DONOR/accept"), body("v5（2026-01-01）")), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TERMS_VERSION_MISMATCH"));

        assertThat(consents.countByUserIdAndTermsType(donorId, TermsType.DONOR)).isZero();
    }

    @Test
    @DisplayName("同意的是舊版本：需要重新同意，重新同意後舊紀錄還在")
    void olderConsentMeansReconsentAndKeepsHistory() throws Exception {
        consents.saveAndFlush(new TermsConsent(donorId, TermsType.DONOR, "v5（2026-01-01）"));

        mvc.perform(as(get("/api/me/terms"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].acceptedVersion").value("v5（2026-01-01）"))
                .andExpect(jsonPath("$[0].upToDate").value(false));

        mvc.perform(as(withBody(post("/api/me/terms/DONOR/accept"), body(CURRENT)), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upToDate").value(true))
                .andExpect(jsonPath("$.acceptedVersion").value(CURRENT));

        // 歷史表只增不改：舊版本與新版本的同意都在
        assertThat(consents.countByUserIdAndTermsType(donorId, TermsType.DONOR)).isEqualTo(2);
        assertThat(consents.findAll()).extracting(TermsConsent::getVersion)
                .containsExactlyInAnyOrder("v5（2026-01-01）", CURRENT);
    }

    @Test
    @DisplayName("每個人只看得到自己的同意狀態")
    void consentIsPerUser() throws Exception {
        mvc.perform(as(withBody(post("/api/me/terms/DONOR/accept"), body(CURRENT)), DONOR))
                .andExpect(status().isOk());

        mvc.perform(as(get("/api/me/terms"), OTHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].upToDate").value(false));
    }

    @Test
    @DisplayName("未登入不能查詢或同意")
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/me/terms")).andExpect(status().isUnauthorized());
        mvc.perform(withBody(post("/api/me/terms/DONOR/accept"), body(CURRENT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("認領前要已同意目前版本：沒同意或同意舊版都被擋，同意後可認領")
    void claimRequiresCurrentConsent() throws Exception {
        UUID wishId = publishedWish();

        mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TERMS_NOT_ACCEPTED"));

        consents.saveAndFlush(new TermsConsent(donorId, TermsType.DONOR, "v5（2026-01-01）"));
        mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TERMS_NOT_ACCEPTED"));

        mvc.perform(as(withBody(post("/api/me/terms/DONOR/accept"), body(CURRENT)), DONOR))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("合作機構須知是另一份：兩份各自記錄，互不影響")
    void orgTermsAreTrackedSeparately() throws Exception {
        mvc.perform(as(get("/api/me/terms"), DONOR))
                .andExpect(jsonPath("$[1].type").value("ORG"))
                .andExpect(jsonPath("$[1].title").value("合作機構參與須知"))
                .andExpect(jsonPath("$[1].currentVersion").value(ORG_CURRENT))
                .andExpect(jsonPath("$[1].upToDate").value(false));

        // 認領者須知的版本不能拿來同意機構須知
        mvc.perform(as(withBody(post("/api/me/terms/ORG/accept"), body(CURRENT)), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TERMS_VERSION_MISMATCH"));

        mvc.perform(as(withBody(post("/api/me/terms/ORG/accept"), body(ORG_CURRENT)), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORG"))
                .andExpect(jsonPath("$.upToDate").value(true));

        mvc.perform(as(get("/api/me/terms"), DONOR))
                .andExpect(jsonPath("$[0].upToDate").value(false))
                .andExpect(jsonPath("$[1].upToDate").value(true));
    }

    @Test
    @DisplayName("機構送出申請前要已同意目前版本的機構須知")
    void organizationRegistrationRequiresCurrentOrgConsent() throws Exception {
        var request = new com.onlinesanta.organization.dto.OrganizationRegistrationRequest(
                "須知之家", "王承辦", "contact@example.org", "02-1234-5678", "0912-345-678",
                "台北市中正區某路 1 號", "平日上午9:00-下午5:00", "服務失依兒童");

        mvc.perform(as(withBody(post("/api/organizations"), request), OTHER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TERMS_NOT_ACCEPTED"));

        // 同意的是舊版本也不算
        UUID otherId = users.findByEmailIgnoreCase(OTHER).orElseThrow().getId();
        consents.saveAndFlush(new TermsConsent(otherId, TermsType.ORG, "v6（2026-01-01）"));
        mvc.perform(as(withBody(post("/api/organizations"), request), OTHER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TERMS_NOT_ACCEPTED"));

        mvc.perform(as(withBody(post("/api/me/terms/ORG/accept"), body(ORG_CURRENT)), OTHER))
                .andExpect(status().isOk());
        mvc.perform(as(withBody(post("/api/organizations"), request), OTHER))
                .andExpect(status().isCreated());
        assertThat(consents.countByUserIdAndTermsType(otherId, TermsType.ORG)).isEqualTo(2);
    }

    private UUID publishedWish() {
        Organization org = Organization.register("送禮之家", "王承辦", "contact@example.org",
                "02-1234-5678", "0912-345-678", "台北市中正區某某路 1 號", null, null);
        org.approve(null, "測試資料");
        organizations.save(org);
        Wish wish = Wish.draft(org, "小星", AgeRange.AGE_7_9, "畫畫",
                "要被認領的願望", "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        return wishes.save(wish).getId();
    }
}
