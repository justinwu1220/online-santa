package com.onlinesanta.closing;

import java.util.UUID;

import com.onlinesanta.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 檔期某個月的財務資料（管理員手動填寫，系統沒有這些資料）。
 *
 * <p>三個金額欄位 NULL 代表「待補」，文件上寫待補；填 0 代表已核實真的沒有發生。兩者不能混用——
 * 純物資活動也不能預設現金與利息是 0，沒核實就不能寫 0。
 */
@Entity
@Table(name = "campaign_finance")
public class CampaignFinance extends BaseEntity {

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Column(name = "period_year", nullable = false, updatable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false, updatable = false)
    private int periodMonth;

    @Column(name = "cash_income")
    private Long cashIncome;

    private Long interest;

    @Column(name = "cash_expense")
    private Long cashExpense;

    @Column(length = 300)
    private String note;

    protected CampaignFinance() {
    }

    public CampaignFinance(UUID campaignId, int periodYear, int periodMonth) {
        this.campaignId = campaignId;
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
    }

    public void update(Long cashIncome, Long interest, Long cashExpense, String note) {
        this.cashIncome = cashIncome;
        this.interest = interest;
        this.cashExpense = cashExpense;
        this.note = note == null || note.isBlank() ? null : note.strip();
    }

    public boolean isEmpty() {
        return cashIncome == null && interest == null && cashExpense == null && note == null;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public int getPeriodYear() {
        return periodYear;
    }

    public int getPeriodMonth() {
        return periodMonth;
    }

    public Long getCashIncome() {
        return cashIncome;
    }

    public Long getInterest() {
        return interest;
    }

    public Long getCashExpense() {
        return cashExpense;
    }

    public String getNote() {
        return note;
    }
}
