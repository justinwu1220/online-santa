package com.onlinesanta.closing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DonationReceiptRepository
        extends JpaRepository<DonationReceipt, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<DonationReceipt> {

    Optional<DonationReceipt> findByReceiptNo(String receiptNo);

    /** 這個願望目前有效的收據（最多一張，資料庫部分唯一索引保證）。 */
    @Query("select r from DonationReceipt r where r.claimId = :claimId and r.status = com.onlinesanta.closing.ReceiptStatus.ISSUED")
    Optional<DonationReceipt> findValidByClaimId(@Param("claimId") UUID claimId);

    List<DonationReceipt> findAllByClaimIdOrderByCreatedAtAsc(UUID claimId);

    List<DonationReceipt> findAllByCampaignIdOrderByReceiptNoAsc(UUID campaignId);

    /** 檔期內有效、PDF 還不是 READY 的收據（待產生、失敗）。 */
    @Query("""
            select r from DonationReceipt r
            where r.campaignId = :campaignId
              and r.status = com.onlinesanta.closing.ReceiptStatus.ISSUED
              and r.pdfStatus <> com.onlinesanta.closing.PdfStatus.READY
            order by r.receiptNo
            """)
    List<DonationReceipt> findNeedingPdf(@Param("campaignId") UUID campaignId);

    long countByCampaignIdAndStatus(UUID campaignId, ReceiptStatus status);

    /** 暫存期限已過、檔案該清除的收據 PDF。 */
    List<DonationReceipt> findByPdfStatusAndPdfExpiresAtBefore(PdfStatus pdfStatus, java.time.Instant cutoff);
}
