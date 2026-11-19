package com.onlinesanta.closing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.attachment.Attachment;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.attachment.AttachmentRepository;
import com.onlinesanta.attachment.UploadStatus;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.closing.dto.ReceiptCheckView;
import com.onlinesanta.closing.dto.ReceiptCheckView.Issue;
import com.onlinesanta.closing.dto.ReceiptCheckView.Severity;

/**
 * 檔期的收據檢查：完成的認領是否都有有效收據、編號是否連續、PDF 是否都能產生、證號與發票佐證是否齊全。
 * 範圍是 {@code [本檔期勸募開始日, 下個檔期勸募開始日)} 內實際收到的認領。
 *
 * <p>只列出差異，不修改任何資料。「無佐證」（沒有發票）只是提醒，不擋匯出。
 */
@Service
public class ReceiptCheckService {

    private static final Pattern SEQUENCE = Pattern.compile("(\\d+)$");
    private static final int MAX_LISTED = 20;

    private final CampaignService campaigns;
    private final DonationReceiptRepository receipts;
    private final ClaimRepository claims;
    private final AttachmentRepository attachments;

    public ReceiptCheckService(CampaignService campaigns, DonationReceiptRepository receipts,
                               ClaimRepository claims, AttachmentRepository attachments) {
        this.campaigns = campaigns;
        this.receipts = receipts;
        this.claims = claims;
        this.attachments = attachments;
    }

    @Transactional(readOnly = true)
    public ReceiptCheckView check(UUID campaignId) {
        Campaign campaign = campaigns.get(campaignId);
        List<DonationReceipt> all = receipts.findAllByCampaignIdOrderByReceiptNoAsc(campaignId);
        List<DonationReceipt> valid = all.stream().filter(r -> r.getStatus() == ReceiptStatus.ISSUED).toList();
        int voided = all.size() - valid.size();

        List<Issue> issues = new ArrayList<>();

        // 完成了卻沒有有效收據
        // 範圍同統計與收據歸屬：[本檔期勸募開始日, 下個檔期勸募開始日)
        java.time.LocalDate toExclusive = campaigns.nextCollectionStartAfter(campaign.getCollectionStart())
                .orElse(java.time.LocalDate.of(9999, 12, 31));
        List<Claim> missing = claims.findCompletedWithoutReceipt(campaign.getCollectionStart(), toExclusive);
        for (Claim claim : missing) {
            issues.add(new Issue(Severity.ERROR, "MISSING_RECEIPT", null,
                    "已完成的願望「%s」（認領 %s）沒有有效收據：%s".formatted(
                            claim.getWish().getTitle(), shortId(claim.getId()), missingReason(claim, campaign))));
        }

        issues.addAll(numberGaps(all));

        // PDF 狀態與證號
        int ready = 0;
        int pending = 0;
        int failed = 0;
        long total = 0;
        for (DonationReceipt r : valid) {
            total += r.getGiftValue();
            switch (r.getPdfStatus()) {
                case READY -> ready++;
                case PENDING -> pending++;
                case FAILED -> {
                    failed++;
                    issues.add(new Issue(Severity.ERROR, "PDF_FAILED", r.getReceiptNo(),
                            "PDF 無法產生：" + (r.getPdfError() == null ? "原因不明" : r.getPdfError())));
                }
            }
            if (r.getPdfStatus() == PdfStatus.PENDING) {
                issues.add(new Issue(Severity.WARNING, "PDF_PENDING", r.getReceiptNo(),
                        "PDF 還沒產生（或暫存已到期被清除），匯出時會自動補產"));
            }
            if (r.isReceiptRequested() && r.getDonorIdEncrypted() == null) {
                issues.add(new Issue(Severity.ERROR, "MISSING_ID_NUMBER", r.getReceiptNo(),
                        "捐贈者要求收據，但沒有證號資料"));
            }
        }

        issues.addAll(missingEvidence(valid));

        return new ReceiptCheckView(campaignId, campaign.getName(), Instant.now(),
                valid.size() + missing.size(), valid.size(), voided, total,
                ready, pending, failed, issues);
    }

    /** 編號中間有缺：作廢的號碼仍留在資料裡，所以缺號代表資料被刪過或配號異常。 */
    private List<Issue> numberGaps(List<DonationReceipt> all) {
        Set<Long> present = new HashSet<>();
        for (DonationReceipt r : all) {
            Matcher m = SEQUENCE.matcher(r.getReceiptNo());
            if (m.find()) {
                present.add(Long.parseLong(m.group(1)));
            }
        }
        if (present.size() < 2) {
            return List.of();
        }
        TreeSet<Long> sorted = new TreeSet<>(present);
        List<Long> gaps = new ArrayList<>();
        for (long n = sorted.first(); n <= sorted.last(); n++) {
            if (!present.contains(n)) {
                gaps.add(n);
            }
        }
        if (gaps.isEmpty()) {
            return List.of();
        }
        String listed = gaps.stream().limit(MAX_LISTED).map(String::valueOf).collect(Collectors.joining("、"));
        return List.of(new Issue(Severity.ERROR, "NUMBER_GAP", null,
                "收據編號不連續，缺少流水號 %s%s".formatted(listed, gaps.size() > MAX_LISTED ? " 等 " + gaps.size() + " 個" : "")));
    }

    /** 沒有發票佐證的有效收據：只提醒，不擋匯出。 */
    private List<Issue> missingEvidence(List<DonationReceipt> valid) {
        if (valid.isEmpty()) {
            return List.of();
        }
        Set<UUID> withProof = attachments.findByPurposeAndOwnerIdInAndUploadStatus(
                        AttachmentPurpose.RECEIPT_PROOF, valid.stream().map(DonationReceipt::getClaimId).toList(),
                        UploadStatus.CONFIRMED)
                .stream().map(Attachment::getOwnerId).collect(Collectors.toSet());
        return valid.stream().filter(r -> !withProof.contains(r.getClaimId()))
                .map(r -> new Issue(Severity.WARNING, "NO_EVIDENCE", r.getReceiptNo(),
                        "沒有上傳發票佐證（時價 %d 元）".formatted(r.getGiftValue())))
                .toList();
    }

    private static String missingReason(Claim claim, Campaign campaign) {
        if (claim.getGiftDescription() == null || claim.getGiftQuantity() == null || claim.getGiftPrice() == null) {
            return "缺少禮物資料（品項、數量或金額），無法開立";
        }
        if (!campaign.isActive()) {
            return "檔期已結案，不再配發收據編號";
        }
        return "開立失敗，請聯絡系統管理員檢查";
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
