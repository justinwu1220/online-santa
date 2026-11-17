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
 * <p>註冊不會檢查註冊者是否真的擁有那個信箱，要輸入寄到信箱的驗證碼才算數。
 * 驗證狀態存在資料庫（{@code users.email_verified_at}），每個請求都回資料庫查。
 *
 * <p>這些測試守住因此而生的攻擊面（管理員權限提升、帳號接管），以及
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

        User member = User.newDonor(ORG_USER, ORG_USER);
        member.markEmailVerified(java.time.Instant.now());
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
                "假機構", "王承辦", "fake@example.org", "02-1234-5678", "0912-345-678",
                "台北市某路 1 號", "平日上午9:00-下午5:00", "服務失依兒童");

        mvc.perform(asUnverified(withBody(post("/api/organizations"), request), NEWCOMER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));
    }

    // ------------------------------------------------------------ 漏洞 A

    @Test
    @DisplayName("用白名單信箱註冊但未驗證，不會取得 ADMIN")
    void unverifiedWhitelistedEmailDoesNotBecomeAdmin() throws Exception {
        // 註冊不檢查你是否擁有那個信箱。少了這道防線，
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

        // 使用者輸入了驗證碼，資料庫的驗證狀態變了，下一個請求就生效
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
        // （例如驗證狀態被重設），那時舊有的權限不該還能使用
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

    // ------------------------------------------------------------ 帳號不能被接管

    @Test
    @DisplayName("拿別人的信箱註冊，不能接管既有帳號")
    void registeringWithAnExistingEmailIsRejected() throws Exception {
        UUID originalId = users.findByEmailIgnoreCase(ORG_USER).orElseThrow().getId();

        // 攻擊者用機構成員的信箱註冊。信箱已經有帳號，一律回 409，
        // 不會建立新帳號，更不會碰到既有帳號
        mvc.perform(withBody(post("/api/auth/register"), java.util.Map.of(
                        "email", ORG_USER, "password", "attacker-password-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_TAKEN"));

        assertThat(users.findByEmailIgnoreCase(ORG_USER).orElseThrow().getId()).isEqualTo(originalId);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("大小寫不同的同一個信箱也算已註冊")
    void emailUniquenessIgnoresCase() throws Exception {
        mvc.perform(withBody(post("/api/auth/register"), java.util.Map.of(
                        "email", ORG_USER.toUpperCase(), "password", "attacker-password-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_TAKEN"));
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
