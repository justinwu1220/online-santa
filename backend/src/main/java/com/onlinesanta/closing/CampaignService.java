package com.onlinesanta.closing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.closing.dto.WishListingView;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.organization.OrganizationRepository;

/**
 * 活動檔期的管理與收據配號。
 *
 * <p>檔期的勸募期間不可重疊（含已結束的）：收據以實際收到日歸屬檔期、營運統計以週期歸屬檔期，
 * 重疊會讓同一天的資料不知道該歸誰。
 */
@Service
public class CampaignService {

    private final CampaignRepository campaigns;
    private final OrganizationRepository organizations;

    public CampaignService(CampaignRepository campaigns, OrganizationRepository organizations) {
        this.campaigns = campaigns;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public List<Campaign> list() {
        return campaigns.findAllByOrderByCollectionStartDescCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Campaign get(UUID id) {
        return campaigns.findById(id).orElseThrow(() -> ResourceNotFoundException.of("活動檔期", id));
    }

    @Transactional
    public Campaign create(CampaignRequest request) {
        String prefix = request.receiptPrefix() == null || request.receiptPrefix().isBlank()
                ? "OS" + request.collectionStart().getYear()
                : request.receiptPrefix();
        if (campaigns.existsByReceiptPrefix(prefix)) {
            throw new BusinessRuleException("RECEIPT_PREFIX_TAKEN", "收據前綴「%s」已被別的檔期使用".formatted(prefix));
        }
        requireNoOverlap(request.collectionStart(), request.collectionEnd(), null);
        requireAssociationOrg(request.associationOrgId());

        Campaign campaign = new Campaign(request.name().strip(), request.collectionStart(), request.collectionEnd(),
                request.useStart(), request.useEnd(), request.wishListStart(), request.wishListEnd(), prefix,
                request.firstReceiptSeq() == null ? 1 : request.firstReceiptSeq());
        campaign.update(request.name().strip(), request.activityNo(), request.permitNo(), request.approvedName(),
                request.collectionStart(), request.collectionEnd(), request.useStart(), request.useEnd(),
                request.wishListStart(), request.wishListEnd(), prefix, request.firstReceiptSeq(), request.associationOrgId(),
                CampaignStatus.ACTIVE);
        return save(campaign);
    }

    @Transactional
    public Campaign update(UUID id, CampaignRequest request) {
        Campaign campaign = campaigns.findByIdForUpdate(id)
                .orElseThrow(() -> ResourceNotFoundException.of("活動檔期", id));
        String prefix = request.receiptPrefix() == null || request.receiptPrefix().isBlank()
                ? campaign.getReceiptPrefix()
                : request.receiptPrefix();
        if (!prefix.equals(campaign.getReceiptPrefix()) && campaigns.existsByReceiptPrefix(prefix)) {
            throw new BusinessRuleException("RECEIPT_PREFIX_TAKEN", "收據前綴「%s」已被別的檔期使用".formatted(prefix));
        }
        CampaignStatus status = request.status() == null ? campaign.getStatus() : request.status();
        requireNoOverlap(request.collectionStart(), request.collectionEnd(), id);
        requireAssociationOrg(request.associationOrgId());

        campaign.update(request.name().strip(), request.activityNo(), request.permitNo(), request.approvedName(),
                request.collectionStart(), request.collectionEnd(), request.useStart(), request.useEnd(),
                request.wishListStart(), request.wishListEnd(), prefix, request.firstReceiptSeq(), request.associationOrgId(), status);
        return save(campaign);
    }

    /**
     * 收據歸屬的檔期：勸募開始日在實際收到日（含）之前、開始日最晚的那一個，範圍是
     * {@code [本檔期勸募開始日, 下個檔期勸募開始日)}，與營運統計同一個定義。
     * 該檔期已結束時仍回傳它（由呼叫端決定能不能配號）。
     */
    @Transactional(readOnly = true)
    public Optional<Campaign> campaignForReceivedOn(LocalDate receivedOn) {
        return campaigns.findFirstByCollectionStartLessThanEqualOrderByCollectionStartDesc(receivedOn);
    }

    /** 這個勸募開始日之後的下一個檔期的開始日；沒有下一個檔期就是 empty（範圍一路到今天）。 */
    @Transactional(readOnly = true)
    public Optional<LocalDate> nextCollectionStartAfter(LocalDate start) {
        return campaigns.findFirstByCollectionStartGreaterThanOrderByCollectionStartAsc(start)
                .map(Campaign::getCollectionStart);
    }

    /** 機構新增願望的關卡：今天不在任何進行中檔期的上架期間內就擋下。修改與審核不呼叫這個。 */
    @Transactional(readOnly = true)
    public void requireWishListingOpen(LocalDate today) {
        if (campaigns.findListingOpen(today).isEmpty()) {
            throw new BusinessRuleException("WISH_LISTING_CLOSED",
                    "目前不在願望上架期間，無法新增願望。上架期間由管理員在活動檔期設定。");
        }
    }

    @Transactional(readOnly = true)
    public WishListingView wishListing(LocalDate today) {
        List<Campaign> open = campaigns.findListingOpen(today);
        if (!open.isEmpty()) {
            return new WishListingView(true, open.get(0).getWishListStart(), open.get(0).getWishListEnd());
        }
        return campaigns.findFirstByStatusAndWishListStartAfterOrderByWishListStartAsc(CampaignStatus.ACTIVE, today)
                .map(c -> new WishListingView(false, c.getWishListStart(), c.getWishListEnd()))
                .orElse(new WishListingView(false, null, null));
    }

    /**
     * 為檔期配一個收據編號。<strong>必須在呼叫端的交易內執行</strong>：鎖住檔期那一列直到交易結束，
     * 號碼才不會被別的交易搶走；交易回滾時號碼一起回滾（沒有開出收據就不該佔號）。
     */
    @Transactional
    public String allocateReceiptNo(UUID campaignId) {
        Campaign campaign = campaigns.findByIdForUpdate(campaignId)
                .orElseThrow(() -> ResourceNotFoundException.of("活動檔期", campaignId));
        if (!campaign.isActive()) {
            throw new BusinessRuleException("CAMPAIGN_CLOSED", "這個檔期已結束，不能再配發收據編號");
        }
        return campaign.allocateReceiptNo();
    }

    /**
     * 同 {@link #allocateReceiptNo}，但檔期已結束時回 empty 而不丟例外。
     * 願望完成的交易裡用這個：丟出例外經過交易代理會把整個交易標成只能回滾，願望就完成不了。
     */
    @Transactional
    public Optional<String> tryAllocateReceiptNo(UUID campaignId) {
        return campaigns.findByIdForUpdate(campaignId)
                .filter(Campaign::isActive)
                .map(Campaign::allocateReceiptNo);
    }

    private void requireNoOverlap(LocalDate start, LocalDate end, UUID excludeId) {
        if (campaigns.countOverlapping(start, end, excludeId) > 0) {
            throw new BusinessRuleException("CAMPAIGN_PERIOD_OVERLAP",
                    "勸募期間與另一個檔期重疊：收據與營運統計都以日期歸屬檔期，期間不能重疊（已結束的檔期也一樣）");
        }
    }

    private void requireAssociationOrg(UUID organizationId) {
        if (organizationId != null && !organizations.existsById(organizationId)) {
            throw new BusinessRuleException("ASSOCIATION_ORG_NOT_FOUND", "找不到指定的協會自身服務對象機構");
        }
    }

    private Campaign save(Campaign campaign) {
        try {
            return campaigns.saveAndFlush(campaign);
        } catch (DataIntegrityViolationException e) {
            // 並發新增同一個前綴時，資料庫的唯一索引是最後防線
            throw new BusinessRuleException("RECEIPT_PREFIX_TAKEN", "收據前綴已被別的檔期使用");
        }
    }
}
