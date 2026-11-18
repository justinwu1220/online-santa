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

    private static CampaignRequest request(String name, String prefix, LocalDate start, LocalDate end) {
        return new CampaignRequest(name, "115000877", null, null, start, end, start, end, null,
                prefix, null, null, null);
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
                .andExpect(jsonPath("$.permitNo").doesNotExist());

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
        CampaignRequest close = new CampaignRequest("第一檔", "115000877", null, null,
                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), LocalDate.of(2026, 11, 25),
                LocalDate.of(2027, 1, 25), null, "OS2026", null, null, CampaignStatus.CLOSED);
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
        UUID id = createViaApi(new CampaignRequest("鎖定", null, null, null,
                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), null, "OS2026", 100, null, null));
        assertThat(campaignService.allocateReceiptNo(id)).isEqualTo("OS2026-000100");

        mvc.perform(as(withBody(put("/api/admin/campaigns/{id}", id),
                        request("鎖定", "OS9999", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25))), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RECEIPT_NUMBERING_LOCKED"));

        // 其他設定（例如補上許可文號）仍可修改，編號不變
        mvc.perform(as(withBody(put("/api/admin/campaigns/{id}", id),
                        new CampaignRequest("鎖定", "115000877", "衛部救字第1150000號", null,
                                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), null,
                                "OS2026", null, null, null)), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitNo").value("衛部救字第1150000號"))
                .andExpect(jsonPath("$.nextReceiptNo").value("OS2026-000101"));
    }

    @Test
    @DisplayName("結束的檔期不再配號，也不再被捐贈日對應到")
    void closedCampaignStopsIssuing() throws Exception {
        UUID id = createViaApi(request("結束", "OS2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25)));
        assertThat(campaignService.findForDonation(LocalDate.of(2026, 12, 25))).isPresent();
        assertThat(campaignService.findForDonation(LocalDate.of(2027, 2, 1))).isEmpty();

        mvc.perform(as(withBody(put("/api/admin/campaigns/{id}", id),
                        new CampaignRequest("結束", "115000877", null, null,
                                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), null,
                                "OS2026", null, null, CampaignStatus.CLOSED)), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(campaignService.findForDonation(LocalDate.of(2026, 12, 25))).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> campaignService.allocateReceiptNo(id))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("已結束");
    }
}
