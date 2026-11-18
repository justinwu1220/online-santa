package com.onlinesanta.closing;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.closing.pdf.ReceiptData;
import com.onlinesanta.closing.pdf.ReceiptLayoutException;
import com.onlinesanta.closing.pdf.ReceiptPdfRenderer;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.receipt.ClaimReceipt;
import com.onlinesanta.receipt.ClaimReceiptRepository;
import com.onlinesanta.receipt.ReceiptCrypto;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageBucket;

/**
 * 捐贈收據：願望完成時開立（配號＋快照），PDF 提交後產生並暫存，過期可重新產生。
 *
 * <p>開立與配號在願望完成的同一個交易裡（{@link #issueFor}）：號碼與收據列一起提交或一起回滾，
 * 不會有「願望完成了卻沒號」或「佔了號卻沒有收據」。PDF 在交易之外產生，失敗不影響願望完成。
 *
 * <p>收據資料是開立當下的快照；PDF 只是呈現。檔期的許可文號等在第一次產生 PDF 時凍結進收據，
 * 之後重新產生內容一致。
 */
@Service
public class DonationReceiptService {

    private static final Logger log = LoggerFactory.getLogger(DonationReceiptService.class);
    private static final String KIND_NAME = "善心人士";

    private final DonationReceiptRepository receipts;
    private final CampaignRepository campaigns;
    private final CampaignService campaignService;
    private final ClaimRepository claims;
    private final ClaimReceiptRepository claimReceipts;
    private final ReceiptCrypto crypto;
    private final ObjectStorage storage;
    private final ReceiptPdfRenderer renderer;
    private final ClosingProperties properties;
    private final ApplicationEventPublisher events;

    public DonationReceiptService(DonationReceiptRepository receipts, CampaignRepository campaigns,
                                  CampaignService campaignService, ClaimRepository claims,
                                  ClaimReceiptRepository claimReceipts, ReceiptCrypto crypto,
                                  ObjectStorage storage, ReceiptPdfRenderer renderer,
                                  ClosingProperties properties, ApplicationEventPublisher events) {
        this.receipts = receipts;
        this.campaigns = campaigns;
        this.campaignService = campaignService;
        this.claims = claims;
        this.claimReceipts = claimReceipts;
        this.crypto = crypto;
        this.storage = storage;
        this.renderer = renderer;
        this.properties = properties;
        this.events = events;
    }

    // ------------------------------------------------------------ 開立

    /**
     * 為完成的認領開立收據。必須在願望完成的交易內呼叫。
     *
     * <p>以下情況不開立（回 empty，不丟例外——例外會讓願望完成不了）：已有有效收據、沒有寄出日或禮物資料、
     * 沒有涵蓋捐贈日的進行中檔期。這些都會列入收據檢查的「待補」，補好檔期後可再補開。
     */
    @Transactional
    public Optional<DonationReceipt> issueFor(Claim claim) {
        if (receipts.findValidByClaimId(claim.getId()).isPresent()) {
            return Optional.empty();
        }
        LocalDate donatedOn = claim.getShippedOn();
        if (donatedOn == null || claim.getGiftDescription() == null || claim.getGiftQuantity() == null
                || claim.getGiftPrice() == null) {
            log.warn("認領 {} 缺少寄出日或禮物資料，不開立收據", claim.getId());
            return Optional.empty();
        }
        Optional<Campaign> campaign = campaignService.findForDonation(donatedOn);
        if (campaign.isEmpty()) {
            log.warn("認領 {} 的捐贈日 {} 沒有涵蓋它的進行中檔期，不開立收據", claim.getId(), donatedOn);
            return Optional.empty();
        }
        Optional<String> number = campaignService.tryAllocateReceiptNo(campaign.get().getId());
        if (number.isEmpty()) {
            return Optional.empty();
        }

        DonationReceipt receipt = new DonationReceipt(claim.getId(), campaign.get().getId(), number.get(),
                LocalDate.now(TaiwanYear.ZONE), snapshotOf(claim));
        receipts.saveAndFlush(receipt);
        events.publishEvent(new DonationReceiptIssuedEvent(receipt.getId()));
        return Optional.of(receipt);
    }

    private DonationReceipt.Snapshot snapshotOf(Claim claim) {
        Optional<ClaimReceipt> requested = claim.isReceiptRequested()
                ? claimReceipts.findByClaimId(claim.getId()) : Optional.empty();
        String name = requested.map(ClaimReceipt::getName).orElseGet(() -> realNameOrKind(claim));
        Organization org = claim.getWish().getOrganization();
        return new DonationReceipt.Snapshot(
                requested.isPresent(),
                name,
                requested.map(ClaimReceipt::getIdNumberEncrypted).orElse(null),
                requested.map(ClaimReceipt::getEmail).orElse(null),
                claim.getShippedOn(),
                claim.getReceivedOn(),
                claim.getGiftDescription(),
                claim.getGiftQuantity(),
                "份",
                claim.getGiftPrice(),
                org.getId(),
                org.getName(),
                org.getWithholdingTaxId());
    }

    /** 收據抬頭用真實姓名（不是公開暱稱）；沒有就是「善心人士」。 */
    private static String realNameOrKind(Claim claim) {
        String real = claim.getDonor().getName();
        return real == null || real.isBlank() ? KIND_NAME : real.strip();
    }

    /**
     * 補開收據：檔期建立或補上之後，為「已完成、寄出日落在勸募期間、卻還沒有有效收據」的認領開立，
     * 依完成時間排序，讓流水號與完成的先後一致。PDF 在提交後產生。
     *
     * @return 這次開立的張數
     */
    @Transactional
    public int issueMissing(UUID campaignId) {
        Campaign campaign = campaigns.findById(campaignId)
                .orElseThrow(() -> ResourceNotFoundException.of("活動檔期", campaignId));
        int issued = 0;
        for (Claim claim : claims.findCompletedMissingReceipt(campaign.getCollectionStart(), campaign.getCollectionEnd())) {
            if (issueFor(claim).isPresent()) {
                issued++;
            }
        }
        return issued;
    }

    // ------------------------------------------------------------ 作廢重開

    /**
     * 作廢一張收據並重開：舊號保留（狀態作廢、記原因與時間、指向新收據），新收據用目前的認領資料與新號碼。
     * 全部或全無：重開不了（例如已沒有涵蓋的檔期）就整個回滾，舊收據維持有效。
     */
    @Transactional
    public DonationReceipt voidAndReissue(UUID receiptId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("VOID_REASON_REQUIRED", "請填寫作廢原因");
        }
        DonationReceipt old = receipts.findById(receiptId)
                .orElseThrow(() -> ResourceNotFoundException.of("收據", receiptId));
        old.voidWith(reason.strip(), Instant.now());
        receipts.saveAndFlush(old);             // 先釋放「一個願望一張有效收據」的唯一索引

        Claim claim = claims.findWithDetailsById(old.getClaimId())
                .orElseThrow(() -> ResourceNotFoundException.of("認領", old.getClaimId()));
        DonationReceipt replacement = issueFor(claim).orElseThrow(() -> new BusinessRuleException(
                "CANNOT_REISSUE", "無法重開收據：找不到涵蓋捐贈日的進行中檔期，或認領缺少禮物資料"));
        old.replacedBy(replacement.getId());
        deleteFiles(old);
        return replacement;
    }

    // ------------------------------------------------------------ PDF

    /**
     * 產生（或重新產生）收據 PDF 並暫存到私有 bucket。呼叫端不應在願望完成的交易內呼叫。
     * 檔期還沒有許可文號 → 標待補；版面放不下 → 標失敗並記原因；兩者都不丟例外。
     */
    @Transactional
    public DonationReceipt generatePdf(UUID receiptId) {
        DonationReceipt r = receipts.findById(receiptId)
                .orElseThrow(() -> ResourceNotFoundException.of("收據", receiptId));
        if (r.getStatus() != ReceiptStatus.ISSUED) {
            return r;
        }
        Campaign campaign = campaigns.findById(r.getCampaignId())
                .orElseThrow(() -> ResourceNotFoundException.of("活動檔期", r.getCampaignId()));

        String permit = r.hasFrozenCampaignInfo() ? r.getPermitNoUsed() : campaign.getPermitNo();
        if (permit == null || permit.isBlank()) {
            r.markPdfPendingInfo();
            return receipts.save(r);
        }
        r.freezeCampaignInfo(permit,
                campaign.getApprovedName() != null ? campaign.getApprovedName() : campaign.getName(),
                campaign.getCollectionStart(), campaign.getCollectionEnd());

        try {
            var rendered = renderer.render(renderData(r));
            String base = "closing/receipts/" + r.getReceiptNo() + "/";
            storage.put(StorageBucket.PRIVATE, base + "white.pdf", "application/pdf", rendered.white());
            storage.put(StorageBucket.PRIVATE, base + "yellow-red.pdf", "application/pdf", rendered.yellowRed());
            Instant now = Instant.now();
            r.markPdfReady(base + "white.pdf", base + "yellow-red.pdf", now,
                    now.plus(Duration.ofDays(properties.retentionDays())));
        } catch (ReceiptLayoutException e) {
            log.warn("收據 {} 的 PDF 放不進版面：{}", r.getReceiptNo(), e.getMessage());
            r.markPdfFailed(e.getMessage());
        }
        return receipts.save(r);
    }

    /** 取得收據 PDF 的內容；暫存已清除或過期就重新產生。尚缺許可文號或版面失敗時回 empty。 */
    @Transactional
    public Optional<byte[]> readPdf(UUID receiptId, PdfKind kind) {
        DonationReceipt r = receipts.findById(receiptId)
                .orElseThrow(() -> ResourceNotFoundException.of("收據", receiptId));
        if (r.getStatus() == ReceiptStatus.VOIDED) {
            throw new BusinessRuleException("RECEIPT_VOIDED", "這張收據已作廢，沒有可下載的 PDF");
        }
        Optional<byte[]> cached = readCached(r, kind);
        if (cached.isPresent()) {
            return cached;
        }
        r = generatePdf(receiptId);
        return r.getPdfStatus() == PdfStatus.READY ? readCached(r, kind) : Optional.empty();
    }

    private Optional<byte[]> readCached(DonationReceipt r, PdfKind kind) {
        if (r.getPdfStatus() != PdfStatus.READY) {
            return Optional.empty();
        }
        String object = kind == PdfKind.WHITE ? r.getWhiteObject() : r.getYellowRedObject();
        return object == null ? Optional.empty() : storage.read(StorageBucket.PRIVATE, object);
    }

    /**
     * 確保收據 PDF 已在雲端（過期被清掉就重新產生），回傳物件路徑；尚缺許可文號或版面失敗時回 empty。
     * 管理員下載用：回傳路徑讓呼叫端簽發短效下載網址，檔案內容不經過後端。
     */
    @Transactional
    public Optional<String> ensurePdfObject(UUID receiptId, PdfKind kind) {
        DonationReceipt r = receipts.findById(receiptId)
                .orElseThrow(() -> ResourceNotFoundException.of("收據", receiptId));
        if (r.getStatus() == ReceiptStatus.VOIDED) {
            throw new BusinessRuleException("RECEIPT_VOIDED", "這張收據已作廢，沒有可下載的 PDF");
        }
        if (r.getPdfStatus() != PdfStatus.READY || missingFile(r)) {
            r = generatePdf(receiptId);
        }
        if (r.getPdfStatus() != PdfStatus.READY) {
            return Optional.empty();
        }
        return Optional.of(kind == PdfKind.WHITE ? r.getWhiteObject() : r.getYellowRedObject());
    }

    private boolean missingFile(DonationReceipt r) {
        return r.getWhiteObject() == null || r.getYellowRedObject() == null
                || storage.find(StorageBucket.PRIVATE, r.getWhiteObject()).isEmpty()
                || storage.find(StorageBucket.PRIVATE, r.getYellowRedObject()).isEmpty();
    }

    /**
     * 為檔期內所有「PDF 還不是 READY」的有效收據補產（待產生、待補許可文號、失敗）。
     * 管理員填入許可文號後一鍵補產用。回傳 {成功, 仍待補或失敗}。
     */
    @Transactional
    public int[] generateMissing(UUID campaignId) {
        int ready = 0;
        int stillPending = 0;
        List<DonationReceipt> todo = receipts.findNeedingPdf(campaignId);
        for (DonationReceipt r : todo) {
            DonationReceipt result = generatePdf(r.getId());
            if (result.getPdfStatus() == PdfStatus.READY) {
                ready++;
            } else {
                stillPending++;
            }
        }
        return new int[] {ready, stillPending};
    }

    private ReceiptData renderData(DonationReceipt r) {
        String idNumber;
        try {
            idNumber = r.getDonorIdEncrypted() == null ? null : crypto.decrypt(r.getDonorIdEncrypted());
        } catch (IllegalStateException e) {
            // 證號是用建立當時的金鑰加密的；金鑰換過或資料毀損就解不開。標成失敗並說明原因，
            // 不要讓管理員看到「未預期錯誤」
            throw new ReceiptLayoutException("捐贈者證號", "無法解密（加密金鑰與建立當時不同，或資料毀損），請請捐贈者重新填寫收據資料後作廢重開");
        }
        return new ReceiptData(r.getReceiptNo(), r.getIssuedOn(), r.getDonorName(), idNumber, r.getDonatedOn(),
                r.getActivityNameUsed(), r.getPermitNoUsed(), r.getCollectionStartUsed(), r.getCollectionEndUsed(),
                properties.purpose(), r.getGiftDescription(), r.getGiftQuantity(), r.getGiftUnit(),
                r.getGiftValue(), r.getOrganizationName());
    }

    private void deleteFiles(DonationReceipt r) {
        for (String object : new String[] {r.getWhiteObject(), r.getYellowRedObject()}) {
            if (object != null) {
                storage.delete(StorageBucket.PRIVATE, object);
            }
        }
        r.markPdfExpired();
    }
}
