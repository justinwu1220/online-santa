package com.onlinesanta.closing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.closing.dto.FinanceView;
import com.onlinesanta.closing.dto.FinanceView.Month;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

@DisplayName("檔期財務資料（現金、利息、實支、自籌）")
class CampaignFinanceIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String DONOR = "fin-donor@example.com";

    @Autowired CampaignService campaigns;
    @Autowired CampaignFinanceRepository finance;
    @Autowired UserRepository users;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        users.save(User.newDonor(DONOR, "熱心民眾"));
        campaign = campaigns.create(new CampaignRequest("財務檔期", "115000877", "衛部救字第1150000號", null,
                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), LocalDate.of(2026, 11, 25),
                LocalDate.of(2027, 1, 25), LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 20), "OSF", null, null, null));
    }

    @Test
    @DisplayName("讀取：勸募開始日到使用結束日的每個月份都在，金額全是待補（null），不是 0")
    void everyMonthIsListedAndUnknownMeansNull() throws Exception {
        mvc.perform(as(get("/api/admin/campaigns/{id}/finance", campaign.getId()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(3))
                .andExpect(jsonPath("$.months[0].year").value(2026))
                .andExpect(jsonPath("$.months[0].month").value(11))
                .andExpect(jsonPath("$.months[0].cashIncome").doesNotExist())
                .andExpect(jsonPath("$.months[2].month").value(1))
                .andExpect(jsonPath("$.selfFundedAmount").doesNotExist());
    }

    @Test
    @DisplayName("儲存：0 與空白是兩回事；全部空白的月份不留紀錄；自籌寫在檔期上")
    void zeroIsNotTheSameAsBlank() throws Exception {
        FinanceView request = new FinanceView(List.of(
                new Month(2026, 11, 0L, 12L, null, "核實無現金"),
                new Month(2026, 12, null, null, null, null)), 3000L, "本會購買卡片");

        mvc.perform(as(withBody(put("/api/admin/campaigns/{id}/finance", campaign.getId()), request), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[0].cashIncome").value(0))           // 0＝已核實沒有
                .andExpect(jsonPath("$.months[0].interest").value(12))
                .andExpect(jsonPath("$.months[0].cashExpense").doesNotExist())    // 空白＝待補
                .andExpect(jsonPath("$.months[0].note").value("核實無現金"))
                .andExpect(jsonPath("$.months[1].cashIncome").doesNotExist())
                .andExpect(jsonPath("$.selfFundedAmount").value(3000))
                .andExpect(jsonPath("$.selfFundedNote").value("本會購買卡片"));

        assertThat(finance.findAllByCampaignIdOrderByPeriodYearAscPeriodMonthAsc(campaign.getId())).hasSize(1);

        // 把已填的月份清空：紀錄移除
        FinanceView clear = new FinanceView(List.of(new Month(2026, 11, null, null, null, null)), null, null);
        mvc.perform(as(withBody(put("/api/admin/campaigns/{id}/finance", campaign.getId()), clear), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[0].cashIncome").doesNotExist())
                .andExpect(jsonPath("$.selfFundedAmount").doesNotExist());
        assertThat(finance.findAllByCampaignIdOrderByPeriodYearAscPeriodMonthAsc(campaign.getId())).isEmpty();
    }

    @Test
    @DisplayName("負數金額被擋下；只有管理員能讀寫")
    void validationAndAccess() throws Exception {
        FinanceView negative = new FinanceView(List.of(new Month(2026, 11, -1L, null, null, null)), null, null);
        mvc.perform(as(withBody(put("/api/admin/campaigns/{id}/finance", campaign.getId()), negative), ADMIN))
                .andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/admin/campaigns/{id}/finance", campaign.getId()), DONOR))
                .andExpect(status().isForbidden());
    }
}
