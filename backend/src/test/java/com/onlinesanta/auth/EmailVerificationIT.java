package com.onlinesanta.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.organization.dto.OrganizationRegistrationRequest;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

/**
 * 信箱驗證的邊界。
 *
 * <p>只開放 Google 登入時，「email 一定驗證過」是成立的前提——Google 保證。
 * 開放 email/密碼註冊之後就不成立了：Firebase 不會檢查註冊者是否真的擁有那個信箱。
 *
 * <p>這些測試守住兩個因此而生的攻擊面（管理員權限提升、帳號接管），以及
 * 「會產生實質後果的操作需要真實信箱」這條規則。
 */
@DisplayName("信箱驗證")
class EmailVerificationIT extends ApiIntegrationTest {

    private static final String ADMIN_EMAIL = "platform-admin@example.com";
    private static final String ORG_USER = "org@example.org";
    private static final String NEWCOMER = "newcomer@example.com";

    @Autowired
    UserRepository users;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    WishRepository wishes;

    private UUID wishId;

    @BeforeEach
    void setUp() {
        Organization organization = Organization.register(
                "陽光之家", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);

        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);

        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                "色鉛筆", "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        wishId = wishes.save(wish).getId();
    }

    // ------------------------------------------------------------ 允許的事

    @Test
    @DisplayName("未驗證也能登入並取得身分")
    void unverifiedUsersCanStillSignIn() throws Exception {
        mvc.perform(asUnverified(get("/api/me"), NEWCOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(NEWCOMER))
                .andExpect(jsonPath("$.role").value("DONOR"));
    }

    @Test
    @DisplayName("未驗證也能瀏覽願望牆")
    void unverifiedUsersCanBrowse() throws Exception {
        // 先讓人看見孩子的願望，才有動力去收驗證信
        mvc.perform(asUnverified(get("/api/wishes"), NEWCOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(asUnverified(get("/api/wishes/{id}", wishId), NEWCOMER))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 擋下的事

    @Test
    @DisplayName("未驗證不能認領")
    void unverifiedUsersCannotClaim() throws Exception {
        // 機構要靠這個信箱聯繫捐贈者，寄送出問題時也只有這條路
        mvc.perform(asUnverified(post("/api/wishes/{id}/claim", wishId), NEWCOMER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));

        mvc.perform(get("/api/wishes"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("未驗證不能申請機構")
    void unverifiedUsersCannotRegisterAnOrganization() throws Exception {
        // 同上：欄位要填滿，才驗得到「信箱未驗證被擋下」而不是欄位驗證
        var request = new OrganizationRegistrationRequest(
                "假機構", "王承辦", "fake@example.org", "02-1234-5678", "台北市某路 1 號", null);

        mvc.perform(asUnverified(withBody(post("/api/organizations"), request), NEWCOMER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));
    }

    // ------------------------------------------------------------ 漏洞 A

    @Test
    @DisplayName("用白名單信箱註冊但未驗證，不會取得 ADMIN")
    void unverifiedWhitelistedEmailDoesNotBecomeAdmin() throws Exception {
        // Firebase 的密碼註冊不檢查你是否擁有那個信箱。少了這道防線，
        // 任何人用管理員的信箱註冊就能拿到全站孩童資料與捐贈者個資
        mvc.perform(asUnverified(get("/api/me"), ADMIN_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DONOR"));

        assertThat(users.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getRole())
                .as("資料庫裡也不該是 ADMIN")
                .isEqualTo(UserRole.DONOR);

        mvc.perform(asUnverified(get("/api/admin/stats"), ADMIN_EMAIL))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("驗證之後才取得 ADMIN")
    void verifiedWhitelistedEmailBecomesAdmin() throws Exception {
        mvc.perform(asUnverified(get("/api/me"), ADMIN_EMAIL))
                .andExpect(jsonPath("$.role").value("DONOR"));

        // 使用者點了驗證信，下一個 token 就帶 email_verified = true
        mvc.perform(as(get("/api/me"), ADMIN_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(as(get("/api/admin/stats"), ADMIN_EMAIL))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("既有的管理員拿未驗證的 token 也用不了管理權限")
    void existingAdminLosesPrivilegesWhenEmailBecomesUnverified() throws Exception {
        // 先以驗證身分登入取得 ADMIN
        mvc.perform(as(get("/api/me"), ADMIN_EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // 角色提升是單向的，資料庫裡仍是 ADMIN——但信箱有可能變回未驗證
        // （使用者在 Firebase 更換了信箱），那時舊有的權限不該還能使用
        mvc.perform(asUnverified(get("/api/admin/stats"), ADMIN_EMAIL))
                .andExpect(status().isForbidden());

        mvc.perform(asUnverified(get("/api/me"), ADMIN_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.emailVerified").value(false));

        assertThat(users.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getRole())
                .as("資料庫的角色不變，只是這個 token 用不了")
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("既有的機構成員拿未驗證的 token 也用不了機構功能")
    void existingOrgMemberLosesPrivilegesWhenEmailBecomesUnverified() throws Exception {
        mvc.perform(as(get("/api/organizations/me"), ORG_USER))
                .andExpect(status().isOk());

        mvc.perform(asUnverified(get("/api/organizations/me"), ORG_USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ORG_MEMBER"));
    }

    // ------------------------------------------------------------ 漏洞 B

    @Test
    @DisplayName("拿別人的信箱註冊，不能接管既有帳號")
    void unverifiedForeignUidCannotTakeOverAnExistingAccount() throws Exception {
        UUID originalId = users.findByEmailIgnoreCase(ORG_USER).orElseThrow().getId();

        // 攻擊者用機構成員的信箱在 Firebase 註冊密碼帳號，uid 不同且未驗證
        mvc.perform(get("/api/me")
                        .header("Authorization",
                                "Bearer " + TestJwtSupport.unverifiedTokenWithForeignUid(ORG_USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));

        var untouched = users.findByEmailIgnoreCase(ORG_USER).orElseThrow();
        assertThat(untouched.getId()).isEqualTo(originalId);
        assertThat(untouched.getFirebaseUid())
                .as("既有帳號不該被改綁到攻擊者的 uid")
                .isEqualTo(TestJwtSupport.uidFor(ORG_USER));
    }

    @Test
    @DisplayName("Firebase 帳號重建的正當情境仍然可以接管")
    void verifiedForeignUidStillAdoptsTheExistingAccount() throws Exception {
        UUID originalId = users.findByEmailIgnoreCase(ORG_USER).orElseThrow().getId();

        // 這正是 adopt 當初要處理的情境：Firebase 端刪除後以同信箱重建，
        // 本地的認領紀錄應該延續給同一個人。已驗證就放行
        mvc.perform(get("/api/me")
                        .header("Authorization",
                                "Bearer " + TestJwtSupport.tokenFor(ORG_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ORG_MEMBER"));

        assertThat(users.findByEmailIgnoreCase(ORG_USER).orElseThrow().getId())
                .isEqualTo(originalId);
    }

    // ------------------------------------------------------------ 驗證後放行

    @Test
    @DisplayName("驗證之後可以認領")
    void verifiedUsersCanClaim() throws Exception {
        mvc.perform(asUnverified(post("/api/wishes/{id}/claim", wishId), NEWCOMER))
                .andExpect(status().isForbidden());

        mvc.perform(as(post("/api/wishes/{id}/claim", wishId), NEWCOMER))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("錯誤訊息說得出下一步")
    void theErrorTellsTheUserWhatToDo() throws Exception {
        mvc.perform(asUnverified(post("/api/wishes/{id}/claim", wishId), NEWCOMER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString(NEWCOMER),
                                org.hamcrest.Matchers.containsString("垃圾郵件"))));
    }
}
