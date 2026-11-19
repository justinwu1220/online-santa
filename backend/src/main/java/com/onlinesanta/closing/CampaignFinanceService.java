package com.onlinesanta.closing;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.closing.dto.FinanceView;
import com.onlinesanta.closing.dto.FinanceView.Month;

/** 檔期的財務資料（手動欄位）。讀取時補齊每個月份，儲存時空白的月份不留紀錄。 */
@Service
public class CampaignFinanceService {

    private final CampaignRepository campaigns;
    private final CampaignFinanceRepository finance;
    private final CampaignService campaignService;

    public CampaignFinanceService(CampaignRepository campaigns, CampaignFinanceRepository finance,
                                  CampaignService campaignService) {
        this.campaigns = campaigns;
        this.finance = finance;
        this.campaignService = campaignService;
    }

    @Transactional(readOnly = true)
    public FinanceView get(UUID campaignId) {
        Campaign campaign = campaignService.get(campaignId);
        Map<YearMonth, CampaignFinance> saved = new HashMap<>();
        for (CampaignFinance f : finance.findAllByCampaignIdOrderByPeriodYearAscPeriodMonthAsc(campaignId)) {
            saved.put(YearMonth.of(f.getPeriodYear(), f.getPeriodMonth()), f);
        }
        List<Month> months = new ArrayList<>();
        YearMonth from = YearMonth.from(campaign.getCollectionStart());
        YearMonth to = YearMonth.from(campaign.getUseEnd());
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            CampaignFinance f = saved.remove(m);
            months.add(f == null ? new Month(m.getYear(), m.getMonthValue(), null, null, null, null)
                    : new Month(m.getYear(), m.getMonthValue(), f.getCashIncome(), f.getInterest(), f.getCashExpense(), f.getNote()));
        }
        // 檔期日期改過之後，範圍外但已經填過的月份仍然保留顯示，免得資料看起來不見了
        saved.forEach((m, f) -> months.add(new Month(m.getYear(), m.getMonthValue(), f.getCashIncome(),
                f.getInterest(), f.getCashExpense(), f.getNote())));
        months.sort(java.util.Comparator.comparingInt(Month::year).thenComparingInt(Month::month));
        return new FinanceView(months, campaign.getSelfFundedAmount(), campaign.getSelfFundedNote());
    }

    @Transactional
    public FinanceView save(UUID campaignId, FinanceView request) {
        Campaign campaign = campaigns.findByIdForUpdate(campaignId)
                .orElseThrow(() -> com.onlinesanta.common.exception.ResourceNotFoundException.of("活動檔期", campaignId));
        campaign.updateSelfFunded(request.selfFundedAmount(), request.selfFundedNote());

        Map<YearMonth, CampaignFinance> existing = new HashMap<>();
        for (CampaignFinance f : finance.findAllByCampaignIdOrderByPeriodYearAscPeriodMonthAsc(campaignId)) {
            existing.put(YearMonth.of(f.getPeriodYear(), f.getPeriodMonth()), f);
        }
        for (Month m : request.months() == null ? List.<Month>of() : request.months()) {
            YearMonth key = YearMonth.of(m.year(), m.month());
            CampaignFinance row = existing.remove(key);
            if (row == null) {
                row = new CampaignFinance(campaignId, m.year(), m.month());
            }
            row.update(m.cashIncome(), m.interest(), m.cashExpense(), m.note());
            if (row.isEmpty()) {
                if (row.getId() != null) {
                    finance.delete(row);
                }
            } else {
                finance.save(row);
            }
        }
        // 請求裡沒有出現的月份不動
        return get(campaignId);
    }
}
