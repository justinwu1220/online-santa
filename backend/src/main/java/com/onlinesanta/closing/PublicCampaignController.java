package com.onlinesanta.closing;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.closing.dto.CampaignPublicView;
import com.onlinesanta.common.TaiwanYear;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 公開的檔期資訊，給網站頁尾用。
 *
 * <p>勸募許可文號依法要公開揭露，所以這支端點未登入也打得到；相對的，它<strong>只</strong>回
 * 這一個欄位，後台才看得到整個檔期。
 *
 * <p>哪一個檔期：與收據、營運統計、排行榜同一套——勸募開始日在今天（含）之前、開始日最晚的
 * 那一個。勸募期間結束後仍是它，直到下一個檔期開始。
 *
 * <p>頁尾每一個公開頁都有，所以帶五分鐘的 Cache-Control，讓瀏覽器與 CDN 擋下重複請求
 * ——這個後端是 scale to zero 的 Cloud Run（對應的 CDN 設定在 firebase.json）。
 * 文號幾乎不會變，本來可以放更久，但它是法定揭露資訊：管理員改了之後全站最多五分鐘就跟上，
 * 一小時太久。管理員自己的分頁由前端在儲存檔期後強制重抓（lib/campaign.ts）。
 */
@RestController
@RequestMapping("/api/campaigns")
@Tag(name = "活動檔期", description = "公開的檔期資訊")
public class PublicCampaignController {

    private final CampaignService campaigns;

    public PublicCampaignController(CampaignService campaigns) {
        this.campaigns = campaigns;
    }

    @GetMapping("/current")
    @Operation(summary = "目前檔期的公開資訊", description = "網站頁尾顯示的勸募許可文號；沒有檔期時回 null")
    public ResponseEntity<CampaignPublicView> current() {
        String permitNo = campaigns.campaignForReceivedOn(LocalDate.now(TaiwanYear.ZONE))
                .map(Campaign::getPermitNo)
                .orElse(null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new CampaignPublicView(permitNo));
    }
}
