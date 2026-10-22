package com.onlinesanta.wish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimRestriction;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

import jakarta.persistence.EntityManager;

/** 凍結的多原因疊加（V35）與統一的限制判定（ClaimRestriction）。 */
@DisplayName("願望凍結的多原因與認領限制")
class WishFreezeCausesIT extends ApiIntegrationTest {

    private static final String ORG_USER = "causes-org@example.org";
    private static final String DONOR = "causes-donor@example.com";

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired ClaimRepository claimRepository;
    @Autowired WishFreezeService freezes;
    @Autowired EntityManager em;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.register("多原因測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    private UUID claimedClaim() throws Exception {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                "多原因", "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        UUID wishId = wishes.save(wish).getId();
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private Wish wishOf(UUID claimId) {
        em.flush();
        em.clear();
        return claimRepository.findWithDetailsById(claimId).orElseThrow().getWish();
    }

    @Test
    @DisplayName("多個原因疊加：解除其中一個仍然凍結、期限仍暫停，全部解除才恢復")
    void onlyTheLastLiftedCauseUnfreezes() throws Exception {
        UUID claimId = claimedClaim();
        Wish wish = wishOf(claimId);

        var paused = freezes.freeze(wish, WishFreezeCause.ADMIN_OTHER, "調查中，先暫停", null);
        assertThat(paused).isPresent();
        // 第二個原因：願望本來就凍結了，不是狀態改變，不重複通知也不重複暫停
        assertThat(freezes.freeze(wish, WishFreezeCause.ORG_SUSPENDED, "機構停權", null)).isEmpty();
        assertThat(wish.getFrozenCause()).isEqualTo(WishFreezeCause.ORG_SUSPENDED); // 優先度較高
        assertThat(ClaimRestriction.of(wish)).isEqualTo(ClaimRestriction.ORG_SUSPENDED);

        // 機構復權：只解除機構停權這個原因，管理員原本的凍結還在
        assertThat(freezes.liftCause(wish, WishFreezeCause.ORG_SUSPENDED, null)).isEmpty();
        assertThat(wish.isFrozen()).isTrue();
        assertThat(wish.getFrozenCause()).isEqualTo(WishFreezeCause.ADMIN_OTHER);
        assertThat(wish.getFrozenReason()).isEqualTo("調查中，先暫停");
        em.flush();
        em.clear();
        assertThat(claimRepository.findById(claimId).orElseThrow().getDeadlinePausedAt()).isNotNull();

        // 管理員解除：全部解除，期限恢復
        wish = wishOf(claimId);
        assertThat(freezes.liftAdminCauses(wish, null)).isPresent();
        assertThat(wish.isFrozen()).isFalse();
        assertThat(wish.getFrozenCause()).isNull();
        Claim resumed = claimRepository.findWithDetailsById(claimId).orElseThrow();
        assertThat(resumed.getDeadlinePausedAt()).isNull();
        assertThat(ClaimRestriction.of(resumed)).isEqualTo(ClaimRestriction.NONE);
    }

    @Test
    @DisplayName("管理員的解除凍結不能解掉停權連動的原因；管理員原因同時只能有一個")
    void adminUnfreezeCannotLiftSuspensionCauses() throws Exception {
        UUID claimId = claimedClaim();
        Wish wish = wishOf(claimId);
        freezes.freeze(wish, WishFreezeCause.DONOR_SUSPENDED, "認領者違規", null);

        assertThatThrownBy(() -> freezes.liftAdminCauses(wish, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("停權");
        assertThat(wish.isFrozen()).isTrue();

        freezes.freeze(wish, WishFreezeCause.ADMIN_CONTENT, "內容疑慮", null);
        assertThatThrownBy(() -> freezes.freeze(wish, WishFreezeCause.ADMIN_OTHER, "重複", null))
                .isInstanceOf(BusinessRuleException.class);
        // 系統原因冪等
        assertThat(freezes.freeze(wish, WishFreezeCause.DONOR_SUSPENDED, "再來一次", null)).isEmpty();
        assertThat(freezes.activeFreezes(wish.getId())).hasSize(2);
    }

    @Test
    @DisplayName("凍結期間：機構不能收回認領、傳訊；認領者不能傳訊；認領者違規時機構仍可確認收到")
    void restrictedClaimBlocksOrganizationActionsAndMessages() throws Exception {
        UUID claimId = claimedClaim();
        Wish wish = wishOf(claimId);
        freezes.freeze(wish, WishFreezeCause.ADMIN_CONTENT, "內容疑慮", null);
        em.flush();

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                        Map.of("reason", "OTHER")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_RESTRICTED"));
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimId), Map.of("body", "你好")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_RESTRICTED"));
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimId), Map.of("body", "你好")), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_RESTRICTED"));

        // 機構看得到限制與說明，認領者看得到暫停原因
        mvc.perform(as(get("/api/organizations/me/claims/{id}", claimId), ORG_USER))
                .andExpect(jsonPath("$.restriction").value("WISH_FROZEN"))
                .andExpect(jsonPath("$.restrictionMessage").isNotEmpty());
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(jsonPath("$.paused").value(true))
                .andExpect(jsonPath("$.pauseReason").value("WISH_FROZEN"));
    }
}
