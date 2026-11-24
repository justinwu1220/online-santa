package com.onlinesanta.closing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.onlinesanta.closing.doc.ClosingDataService;
import com.onlinesanta.closing.doc.ClosingDataset;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;
import com.onlinesanta.closing.doc.OrgFormPdfWriter;
import com.onlinesanta.notification.MailAttachment;
import com.onlinesanta.notification.MailTemplateId;
import com.onlinesanta.notification.NotificationProperties;
import com.onlinesanta.notification.NotificationService;
import com.onlinesanta.notification.NotificationService.Outcome;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageBucket;

/**
 * 結案寄信：紅聯收據寄給勾選要收據的捐贈人、機構確認表（05A＋05B）寄給受贈機構。
 *
 * <p>流程是「寄送前檢查 → 管理員確認 → 分段寄出」：每次呼叫 {@code send*} 在時間預算內逐封<strong>同步</strong>寄
 * （不用非同步佇列，才知道每一封的結果），前端反覆呼叫到沒有待寄為止。
 * <ul>
 *   <li>不重寄：已寄出的（紅聯 {@code red_sent_at}、機構 SENT 紀錄）一律略過；</li>
 *   <li>失敗的這一輪不再重試（否則前端會無限迴圈），檢查會列出失敗數，管理員按「重寄失敗」才重來；</li>
 *   <li>每日額度用完或寄信未設定：立刻停止，沒寄的維持待寄，隔天或設定好之後續寄；</li>
 *   <li>康乃心自己（白黃聯、05C）不寄信。</li>
 * </ul>
 */
@Service
public class ClosingMailService {

    static final Duration BUDGET = Duration.ofSeconds(15);

    /** 檢查名單一次最多列這麼多列（收據可能幾百張）；計數不受影響。 */
    static final int MAX_TARGETS = 500;

    /** 寄信進度／寄送前檢查。{@code stopped}：LIMIT（每日額度用完）、NOT_CONFIGURED（寄信未設定）或 null。 */
    public record MailProgress(int total, int sent, int failed, int pending, int notReady, int noEmail,
                               int dailyLimit, String stopped, List<Target> targets) {
    }

    /**
     * 檢查名單的一列：機構確認表是一個機構，紅聯是一張收據。
     *
     * @param status    SENT 已寄、PENDING 待寄、FAILED 失敗、NO_EMAIL 沒有信箱、NOT_READY PDF 還沒產生
     * @param reference 收據編號（機構確認表沒有，為 null）
     */
    public record Target(UUID id, String name, String recipient, String status, String error, String reference) {
    }

    private final DonationReceiptRepository receipts;
    private final ClosingMailLogRepository logs;
    private final ClosingDataService data;
    private final OrgFormPdfWriter orgForms;
    private final NotificationService notifications;
    private final NotificationProperties notificationProperties;
    private final ObjectStorage storage;
    private final Clock clock;

    @Autowired
    public ClosingMailService(DonationReceiptRepository receipts, ClosingMailLogRepository logs, ClosingDataService data,
                              OrgFormPdfWriter orgForms, NotificationService notifications,
                              NotificationProperties notificationProperties, ObjectStorage storage) {
        this(receipts, logs, data, orgForms, notifications, notificationProperties, storage, Clock.systemUTC());
    }

    ClosingMailService(DonationReceiptRepository receipts, ClosingMailLogRepository logs, ClosingDataService data,
                       OrgFormPdfWriter orgForms, NotificationService notifications,
                       NotificationProperties notificationProperties, ObjectStorage storage, Clock clock) {
        this.receipts = receipts;
        this.logs = logs;
        this.data = data;
        this.orgForms = orgForms;
        this.notifications = notifications;
        this.notificationProperties = notificationProperties;
        this.storage = storage;
        this.clock = clock;
    }

    // ------------------------------------------------------------ 紅聯收據

    private static boolean wantsRed(DonationReceipt r) {
        return r.getStatus() == ReceiptStatus.ISSUED && r.isReceiptRequested();
    }

    private static boolean hasEmail(DonationReceipt r) {
        return r.getDonorEmail() != null && !r.getDonorEmail().isBlank();
    }

    private static boolean ready(DonationReceipt r) {
        return r.getPdfStatus() == PdfStatus.READY && r.getRedObject() != null;
    }

    public MailProgress previewReceipts(UUID campaignId) {
        return receiptProgress(receipts.findAllByCampaignIdOrderByReceiptNoAsc(campaignId), null);
    }

    /** 寄出待寄的紅聯（一次呼叫在時間預算內做一段）。{@code retryFailed} 先把失敗的改回待寄。 */
    public MailProgress sendReceipts(UUID campaignId, boolean retryFailed) {
        List<DonationReceipt> all = receipts.findAllByCampaignIdOrderByReceiptNoAsc(campaignId);
        if (retryFailed) {
            for (DonationReceipt r : all) {
                if (wantsRed(r) && r.getRedSentAt() == null && r.getRedSendError() != null) {
                    r.markRedSendFailed(null);
                    receipts.save(r);
                }
            }
        }
        long deadline = System.nanoTime() + BUDGET.toNanos();
        String stopped = null;
        for (DonationReceipt r : all) {
            if (System.nanoTime() > deadline) {
                break;
            }
            if (!wantsRed(r) || !hasEmail(r) || !ready(r) || r.getRedSentAt() != null || r.getRedSendError() != null) {
                continue;
            }
            byte[] pdf = storage.read(StorageBucket.PRIVATE, r.getRedObject()).orElse(null);
            if (pdf == null) {
                r.markRedSendFailed("紅聯 PDF 檔案不存在，請先重新產生 PDF");
                receipts.save(r);
                continue;
            }
            Outcome outcome = notifications.deliverTemplate(r.getDonorEmail(), MailTemplateId.CLOSING_RECEIPT_RED,
                    Map.of("recipientName", r.getDonorName(), "giftDescription", r.getGiftDescription(),
                            "organizationName", r.getOrganizationName(), "receiptNo", r.getReceiptNo()),
                    List.of(new MailAttachment("捐贈收據-" + r.getReceiptNo() + ".pdf", "application/pdf", pdf)));
            switch (outcome) {
                case SENT -> {
                    r.markRedSent(Instant.now(clock));
                    receipts.save(r);
                }
                case LIMIT_REACHED -> stopped = "LIMIT";
                case NOT_CONFIGURED -> stopped = "NOT_CONFIGURED";
                default -> {
                    r.markRedSendFailed("寄送失敗（寄信伺服器拒絕或樣板錯誤），可稍後按「重寄失敗」");
                    receipts.save(r);
                }
            }
            if (stopped != null) {
                break;
            }
        }
        return receiptProgress(receipts.findAllByCampaignIdOrderByReceiptNoAsc(campaignId), stopped);
    }

    private MailProgress receiptProgress(List<DonationReceipt> all, String stopped) {
        List<DonationReceipt> wanted = all.stream().filter(ClosingMailService::wantsRed).toList();
        int noEmail = count(wanted, r -> !hasEmail(r));
        List<DonationReceipt> mailable = wanted.stream().filter(ClosingMailService::hasEmail).toList();
        int sent = count(mailable, r -> r.getRedSentAt() != null);
        int failed = count(mailable, r -> r.getRedSentAt() == null && r.getRedSendError() != null);
        int notReady = count(mailable, r -> r.getRedSentAt() == null && r.getRedSendError() == null && !ready(r));
        int pending = count(mailable, r -> r.getRedSentAt() == null && r.getRedSendError() == null && ready(r));
        return new MailProgress(wanted.size(), sent, failed, pending, notReady, noEmail,
                notificationProperties.dailyLimit(), stopped, receiptTargets(wanted));
    }

    /** 紅聯的檢查名單：勾了「要收據」的每一張一列（沒勾的不寄，也不列）。 */
    private static List<Target> receiptTargets(List<DonationReceipt> wanted) {
        return wanted.stream().limit(MAX_TARGETS).map(r -> {
            if (r.getRedSentAt() != null) {
                return target(r, "SENT", null);
            }
            if (r.getRedSendError() != null) {
                return target(r, "FAILED", r.getRedSendError());
            }
            if (!hasEmail(r)) {
                return target(r, "NO_EMAIL", "捐贈者沒有留信箱");
            }
            if (!ready(r)) {
                return target(r, "NOT_READY", "紅聯 PDF 還沒產生，按一次「全部下載」就會補產");
            }
            return target(r, "PENDING", null);
        }).toList();
    }

    private static Target target(DonationReceipt r, String status, String error) {
        return new Target(r.getId(), r.getDonorName(), r.getDonorEmail(), status, error, r.getReceiptNo());
    }

    private static <T> int count(List<T> list, Predicate<T> p) {
        return (int) list.stream().filter(p).count();
    }

    // ------------------------------------------------------------ 機構確認表

    public MailProgress previewOrgForms(UUID campaignId) {
        ClosingDataset d = data.load(campaignId);
        return orgProgress(campaignId, d, null);
    }

    public MailProgress sendOrgForms(UUID campaignId, boolean retryFailed) {
        ClosingDataset d = data.load(campaignId);
        long deadline = System.nanoTime() + BUDGET.toNanos();
        String stopped = null;
        for (OrgGroup g : d.externalOrganizations()) {
            if (System.nanoTime() > deadline) {
                break;
            }
            if (g.email() == null || g.email().isBlank()) {
                continue;
            }
            ClosingMailLog log = logs.findByCampaignIdAndKind(campaignId, ClosingMailLog.KIND_ORG_FORMS).stream()
                    .filter(l -> l.getTargetId().equals(g.organizationId())).findFirst().orElse(null);
            if (log != null && ("SENT".equals(log.getStatus()) || !retryFailed)) {
                continue;
            }
            OrgFormPdfWriter.OrgForms forms = orgForms.buildFor(d, g.organizationId());
            if (forms == null) {
                continue;
            }
            Outcome outcome = notifications.deliverTemplate(g.email(), MailTemplateId.CLOSING_ORG_FORMS,
                    Map.of("recipientName", greeting(g), "organizationName", g.name(),
                            "activityName", d.campaign().getName()),
                    List.of(new MailAttachment("05A_機構物資領受暨發放確認表.pdf", "application/pdf", forms.form05A()),
                            new MailAttachment("05B_個人物資領受證明.pdf", "application/pdf", forms.form05B())));
            ClosingMailLog row = log != null ? log
                    : new ClosingMailLog(campaignId, ClosingMailLog.KIND_ORG_FORMS, g.organizationId(), g.email());
            switch (outcome) {
                case SENT -> {
                    row.markSent(g.email(), Instant.now(clock));
                    logs.save(row);
                }
                case LIMIT_REACHED -> stopped = "LIMIT";
                case NOT_CONFIGURED -> stopped = "NOT_CONFIGURED";
                default -> {
                    row.markFailed(g.email(), "寄送失敗（寄信伺服器拒絕或樣板錯誤），可稍後按「重寄失敗」");
                    logs.save(row);
                }
            }
            if (stopped != null) {
                break;
            }
        }
        return orgProgress(campaignId, d, stopped);
    }

    private static String greeting(OrgGroup g) {
        if (g.contactPerson() != null && !g.contactPerson().isBlank()) {
            return g.contactPerson() + "（" + g.name() + "）";
        }
        return g.name();
    }

    private MailProgress orgProgress(UUID campaignId, ClosingDataset d, String stopped) {
        Map<UUID, ClosingMailLog> byOrg = new HashMap<>();
        logs.findByCampaignIdAndKind(campaignId, ClosingMailLog.KIND_ORG_FORMS).forEach(l -> byOrg.put(l.getTargetId(), l));
        List<Target> targets = new ArrayList<>();
        int sent = 0;
        int failed = 0;
        int pending = 0;
        int noEmail = 0;
        for (OrgGroup g : d.externalOrganizations()) {
            ClosingMailLog l = byOrg.get(g.organizationId());
            if (g.email() == null || g.email().isBlank()) {
                noEmail++;
                targets.add(new Target(g.organizationId(), g.name(), null, "NO_EMAIL", "機構沒有聯絡信箱", null));
            } else if (l != null && "SENT".equals(l.getStatus())) {
                sent++;
                targets.add(new Target(g.organizationId(), g.name(), l.getRecipient(), "SENT", null, null));
            } else if (l != null) {
                failed++;
                targets.add(new Target(g.organizationId(), g.name(), g.email(), "FAILED", l.getError(), null));
            } else {
                pending++;
                targets.add(new Target(g.organizationId(), g.name(), g.email(), "PENDING", null, null));
            }
        }
        return new MailProgress(d.externalOrganizations().size(), sent, failed, pending, 0, noEmail,
                notificationProperties.dailyLimit(), stopped, targets);
    }
}
