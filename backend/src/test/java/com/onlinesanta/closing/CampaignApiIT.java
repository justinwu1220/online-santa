package com.onlinesanta.closing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditLogRepository;
import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

@DisplayName("活動檔期與收據配號")
class CampaignApiIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String DONOR = "campaign-donor@example.com";

    @Autowired
    CampaignService campaignService;

    @Autowired
    CampaignRepository campaigns;

    @Autowired
    AdminAuditLogRepository auditLogs;

    @Autowired
    UserRepository users;

    @BeforeEach
    void setUp() {
        users.save(User.newDonor(DONOR, "熱心民眾"));
    }

    private static final String PERMIT = "衛部救字第1150000號";

    private static CampaignRequest request(String name, String prefix, LocalDate start, LocalDate end) {
        return request(name, prefix, start, end, null, null);
    }

    /** 上架期間預設為整個勸募期間到結束前一天。 */
    private static CampaignRequest request(String name, String prefix, LocalDate start, LocalDate end,
                                           Integer firstSeq, CampaignStatus status) {
        return new CampaignRequest(name, "115000877", PERMIT, null, start, end, start, end,
                start, end.minusDays(1), prefix, firstSeq, null, status);
    }

    private UUID createViaApi(CampaignRequest request) throws Exception {
        String body = mvc.perform(as(withBody(post("/api/admin/campaigns"), request), ADMIN))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    @Test
    @DisplayName("管理員新增檔期：進行中、預設收據編號從 000001 起，並寫稽核")
    void adminCreatesCampaign() throws Exception {
        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        request("線上聖誕老人 2026", "OS2026",
                                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25))), ADMIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.receiptPrefix").value("OS2026"))
                .andExpect(jsonPath("$.nextReceiptNo").value("OS2026-000001"))
                .andExpect(jsonPath("$.receiptsIssued").value(0))
                .andExpect(jsonPath("$.permitNo").value("衛部救字第1150000號"));

        assertThat(auditLogs.findAll()).anySatisfy(log ->
                assertThat(log.getAction()).isEqualTo(AdminAuditAction.SAVE_CAMPAIGN));
    }

    @Test
    @DisplayName("沒填前綴時用 OS＋勸募開始年度")
    void defaultPrefixUsesStartYear() throws Exception {
        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        request("預設前綴", "", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 4, 1))), ADMIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptPrefix").value("OS2027"));
    }

    @Test
    @DisplayName("只有管理員能用；一般使用者 403")
    void nonAdminIsForbidden() throws Exception {
        mvc.perform(as(get("/api/admin/campaigns"), DONOR)).andExpect(status().isForbidden());
        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        request("x", "OS2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25))), DONOR))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("已結束的檔期也不能重疊：營運統計把每個檔期換算成一段不重疊的週期")
    void closedCampaignsCannotOverlapEither() throws Exception {
        UUID first = createViaApi(request("第一檔", "OS2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25)));
        CampaignRequest close = request("第一檔", "OS2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                null, CampaignStatus.CLOSED);
        mvc.perform(as(withBody(put("/api/admin/campaigns/{id}", first), close), ADMIN)).andExpect(status().isOk());

        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        request("與已結束檔期重疊", "OS2027", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1))), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_PERIOD_OVERLAP"));
        // 緊接在後面（不重疊）沒問題
        createViaApi(request("下一檔", "OS2027", LocalDate.of(2027, 1, 26), LocalDate.of(2027, 3, 1)));
    }

    @Test
    @DisplayName("日期、前綴格式有誤時回 400")
    void validatesInput() throws Exception {
        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        request("結束早於開始", "OS2026", LocalDate.of(2027, 1, 25), LocalDate.of(2026, 11, 25))), ADMIN))
                .andExpect(status().isConflict());
        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        request("前綴小寫", "os2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25))), ADMIN))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("勸募期間重疊的進行中檔期、重複的收據前綴都會被擋下")
    void rejectsOverlapAndDuplicatePrefix() throws Exception {
        createViaApi(request("第一檔", "OS2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25)));

        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        request("重疊", "OS2027", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1))), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_PERIOD_OVERLAP"));
        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        request("前綴重複", "OS2026", LocalDate.of(2027, 6, 1), LocalDate.of(2027, 7, 1))), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RECEIPT_PREFIX_TAKEN"));
    }

    @Test
    @DisplayName("收據編號依序配發、不重複；回滾的交易不佔號")
    void allocatesSequentialNumbers() throws Exception {
        UUID id = createViaApi(request("配號", "OS2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25)));

        assertThat(campaignService.allocateReceiptNo(id)).isEqualTo("OS2026-000001");
        assertThat(campaignService.allocateReceiptNo(id)).isEqualTo("OS2026-000002");
        assertThat(campaignService.get(id).getReceiptsIssued()).isEqualTo(2);
        mvc.perform(as(get("/api/admin/campaigns"), ADMIN))
                .andExpect(jsonPath("$[0].nextReceiptNo").value("OS2026-000003"));
    }

    @Test
    @DisplayName("從指定號碼開始配；已配號後前綴與起始號碼鎖定")
    void numberingLocksAfterFirstReceipt() throws Exception {
        UUID id = createViaApi(request("鎖定", "OS2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                100, null));
        assertThat(campaignService.allocateReceiptNo(id)).isEqualTo("OS2026-000100");

        mvc.perform(as(withBody(put("/api/admin/campaigns/{id}", id),
                        request("鎖定", "OS9999", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25))), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RECEIPT_NUMBERING_LOCKED"));

        // 其他設定（例如更正許可文號）仍可修改，編號不變
        mvc.perform(as(withBody(put("/api/admin/campaigns/{id}", id),
                        new CampaignRequest("鎖定", "115000877", "衛部救字第1150001號", null,
                                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 24),
                                "OS2026", null, null, null)), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitNo").value("衛部救字第1150001號"))
                .andExpect(jsonPath("$.nextReceiptNo").value("OS2026-000101"));
    }

    @Test
    @DisplayName("收據歸屬：實際收到日落在 [本檔期勸募開始日, 下個檔期勸募開始日) 就屬於本檔期；結束的檔期不再配號")
    void receivedOnAttributionAndClosedCampaign() throws Exception {
        UUID first = createViaApi(request("第一檔", "OS2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25)));
        UUID second = createViaApi(request("第二檔", "OS2027", LocalDate.of(2027, 6, 1), LocalDate.of(2027, 7, 31)));

        assertThat(campaignService.campaignForReceivedOn(LocalDate.of(2026, 11, 24))).isEmpty();           // 第一檔之前
        assertThat(campaignService.campaignForReceivedOn(LocalDate.of(2026, 11, 25)).orElseThrow().getId()).isEqualTo(first);
        assertThat(campaignService.campaignForReceivedOn(LocalDate.of(2027, 2, 1)).orElseThrow().getId()).isEqualTo(first);   // 兩檔期之間的空檔歸前一檔
        assertThat(campaignService.campaignForReceivedOn(LocalDate.of(2027, 5, 31)).orElseThrow().getId()).isEqualTo(first);
        assertThat(campaignService.campaignForReceivedOn(LocalDate.of(2027, 6, 1)).orElseThrow().getId()).isEqualTo(second);
        assertThat(campaignService.nextCollectionStartAfter(LocalDate.of(2026, 11, 25))).contains(LocalDate.of(2027, 6, 1));
        assertThat(campaignService.nextCollectionStartAfter(LocalDate.of(2027, 6, 1))).isEmpty();

        mvc.perform(as(withBody(put("/api/admin/campaigns/{id}", first),
                        request("第一檔", "OS2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), null, CampaignStatus.CLOSED)), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // 結案後仍歸屬於它（由呼叫端決定能不能配號），但不再配號
        assertThat(campaignService.campaignForReceivedOn(LocalDate.of(2026, 12, 25)).orElseThrow().getId()).isEqualTo(first);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> campaignService.allocateReceiptNo(first))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("已結束");
    }

    @Test
    @DisplayName("勸募許可文號必填")
    void permitIsRequired() throws Exception {
        CampaignRequest noPermit = new CampaignRequest("沒文號", "115000877", " ", null,
                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), LocalDate.of(2026, 11, 25),
                LocalDate.of(2027, 1, 25), LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 24),
                "OS2026", null, null, null);
        mvc.perform(as(withBody(post("/api/admin/campaigns"), noPermit), ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("願望上架期間：結束日必須早於勸募結束日、不能早於開始上架日；兩個日期都必填")
    void validatesWishListingPeriod() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 25);
        LocalDate end = LocalDate.of(2027, 1, 25);
        // 結束上架日等於勸募結束日
        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        new CampaignRequest("太晚", "115000877", PERMIT, null, start, end, start, end,
                                start, end, "OS2026", null, null, null)), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_WISH_LISTING_PERIOD"));
        // 結束上架日早於開始上架日
        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        new CampaignRequest("顛倒", "115000877", PERMIT, null, start, end, start, end,
                                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 9), "OS2026", null, null, null)), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_WISH_LISTING_PERIOD"));
        // 沒填
        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        new CampaignRequest("沒填", "115000877", PERMIT, null, start, end, start, end,
                                null, null, "OS2026", null, null, null)), ADMIN))
                .andExpect(status().isBadRequest());
        // 開始上架日不限制早於勸募開始日（使用者決定）
        mvc.perform(as(withBody(post("/api/admin/campaigns"),
                        new CampaignRequest("提早上架", "115000877", PERMIT, null, start, end, start, end,
                                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 20), "OS2026", null, null, null)), ADMIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wishListStart").value("2026-11-01"))
                .andExpect(jsonPath("$.wishListEnd").value("2027-01-20"));
    }
}
