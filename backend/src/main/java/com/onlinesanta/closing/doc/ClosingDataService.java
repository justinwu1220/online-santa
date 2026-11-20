package com.onlinesanta.closing.doc;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.CampaignService;
import com.onlinesanta.closing.DonationReceipt;
import com.onlinesanta.closing.DonationReceiptRepository;
import com.onlinesanta.closing.ReceiptStatus;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.ClosingDataset.MonthRow;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;
import com.onlinesanta.closing.doc.ClosingDataset.Totals;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;

/**
 * 結案文件共用的資料來源（唯讀）。
 *
 * <p>以有效收據的快照為準；願望標題與孩子暱稱不在收據上，從認領與願望補上（05B 每個願望一組頁要用）。
 * 機構的地址、負責人、聯絡人是目前的資料（收據只凍結機構名稱與扣繳編號），缺的欄位維持 null，
 * 文件上寫「待確認」，不編造。
 */
@Service
public class ClosingDataService {

    private final CampaignService campaigns;
    private final DonationReceiptRepository receipts;
    private final ClaimRepository claims;
    private final OrganizationRepository organizations;

    public ClosingDataService(CampaignService campaigns, DonationReceiptRepository receipts, ClaimRepository claims,
                              OrganizationRepository organizations) {
        this.campaigns = campaigns;
        this.receipts = receipts;
        this.claims = claims;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public ClosingDataset load(UUID campaignId, ClosingStage stage) {
        Campaign campaign = campaigns.get(campaignId);
        LocalDate from = campaign.getCollectionStart();
        LocalDate cutoff = stage.cutoff(campaign);

        List<DonationReceipt> valid = receipts.findAllByCampaignIdOrderByReceiptNoAsc(campaignId).stream()
                .filter(r -> r.getStatus() == ReceiptStatus.ISSUED)
                .toList();
        List<DonationReceipt> inRange = new ArrayList<>();
        List<String> outOfRange = new ArrayList<>();
        for (DonationReceipt r : valid) {
            if (r.getDonatedOn().isBefore(from) || r.getDonatedOn().isAfter(cutoff)) {
                outOfRange.add("%s（%s）".formatted(r.getReceiptNo(), RocDate.slash(r.getDonatedOn())));
            } else {
                inRange.add(r);
            }
        }

        Map<UUID, Claim> claimById = claims.findWithWishByIdIn(inRange.stream().map(DonationReceipt::getClaimId).toList())
                .stream().collect(Collectors.toMap(Claim::getId, c -> c));
        List<GiftRow> gifts = inRange.stream().map(r -> {
            Claim claim = claimById.get(r.getClaimId());
            return new GiftRow(r.getClaimId(), r.getReceiptNo(), r.getDonatedOn(), r.getDonorName(),
                    r.getGiftDescription(), r.getGiftQuantity(), r.getGiftUnit(), r.getGiftValue(),
                    r.getOrganizationId(), r.getOrganizationName(),
                    claim == null ? null : claim.getWish().getChildAlias(),
                    claim == null ? null : claim.getWish().getTitle());
        }).toList();

        return new ClosingDataset(campaign, stage, from, cutoff, gifts, groups(campaign, gifts), months(gifts),
                totals(gifts), receiptLog(campaignId), outOfRange);
    }

    private List<ClosingDataset.ReceiptLogRow> receiptLog(UUID campaignId) {
        List<DonationReceipt> all = receipts.findAllByCampaignIdOrderByReceiptNoAsc(campaignId);
        Map<UUID, String> numberById = all.stream().collect(Collectors.toMap(DonationReceipt::getId, DonationReceipt::getReceiptNo));
        return all.stream().map(r -> new ClosingDataset.ReceiptLogRow(r.getReceiptNo(),
                r.getStatus() == ReceiptStatus.ISSUED ? "有效" : "已作廢", r.getIssuedOn(), r.getDonatedOn(), r.getGiftValue(),
                r.getVoidReason(), r.getReplacedById() == null ? null : numberById.get(r.getReplacedById()))).toList();
    }

    private List<OrgGroup> groups(Campaign campaign, List<GiftRow> gifts) {
        Map<UUID, List<GiftRow>> byOrg = new LinkedHashMap<>();
        for (GiftRow g : gifts) {
            byOrg.computeIfAbsent(g.organizationId(), k -> new ArrayList<>()).add(g);
        }
        Map<UUID, Organization> orgs = organizations.findAllById(byOrg.keySet()).stream()
                .collect(Collectors.toMap(Organization::getId, o -> o));
        List<OrgGroup> groups = new ArrayList<>();
        for (Map.Entry<UUID, List<GiftRow>> e : byOrg.entrySet()) {
            List<GiftRow> rows = e.getValue();
            Organization o = orgs.get(e.getKey());
            String name = o != null ? o.getName() : rows.get(0).organizationName();
            // 收據凍結了機構名稱與扣繳編號，沒有查到機構時（已刪除）退回收據上的名稱
            groups.add(new OrgGroup(e.getKey(), name,
                    o == null ? null : o.getWithholdingTaxId(), o == null ? null : o.getAddress(),
                    o == null ? null : o.getResponsiblePerson(), o == null ? null : o.getContactPerson(),
                    o == null ? null : o.getContactPhone(), o == null ? null : o.getContactEmail(),
                    e.getKey().equals(campaign.getAssociationOrgId()), rows,
                    rows.stream().mapToInt(GiftRow::quantity).sum(),
                    rows.stream().mapToLong(GiftRow::value).sum()));
        }
        groups.sort(Comparator.comparing(OrgGroup::name));
        return groups;
    }

    private static List<MonthRow> months(List<GiftRow> gifts) {
        Map<YearMonth, long[]> byMonth = new java.util.TreeMap<>();
        for (GiftRow g : gifts) {
            long[] acc = byMonth.computeIfAbsent(YearMonth.from(g.donatedOn()), k -> new long[2]);
            acc[0]++;
            acc[1] += g.value();
        }
        return byMonth.entrySet().stream()
                .map(e -> new MonthRow(e.getKey().getYear(), e.getKey().getMonthValue(), (int) e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    private static Totals totals(List<GiftRow> gifts) {
        return new Totals(gifts.size(), gifts.stream().mapToInt(GiftRow::quantity).sum(),
                gifts.stream().mapToLong(GiftRow::value).sum(),
                (int) gifts.stream().map(GiftRow::organizationId).distinct().count());
    }
}
