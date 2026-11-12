package com.onlinesanta.suspension;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditService;
import com.onlinesanta.admin.dto.ReviewDecisionRequest;
import com.onlinesanta.admin.dto.ReviewReasonRequest;
import com.onlinesanta.attachment.AttachmentService;
import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.changelog.ProfileChangeLogService;
import com.onlinesanta.changelog.ProfileChangeTargetType;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.event.OrgSuspensionClaimEvent;
import com.onlinesanta.event.OrganizationSuspensionEvent;
import com.onlinesanta.message.AdminTeamChatService;
import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationService;
import com.onlinesanta.organization.OrganizationStatus;
import com.onlinesanta.unlock.UnlockRequest;
import com.onlinesanta.unlock.UnlockRequestRepository;
import com.onlinesanta.unlock.UnlockRequestView;
import com.onlinesanta.unlock.UnlockStatus;
import com.onlinesanta.unlock.UnlockTargetType;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishFreezeCause;
import com.onlinesanta.wish.WishFreezeService;
import com.onlinesanta.wish.WishRepository;
import com.onlinesanta.wish.WishStatus;

/**
 * 機構違規（冒名、違規行為）的處置：停權、復權、機構的申請解除，以及通知受影響的認領者。
 *
 * <p><strong>停權</strong>：保全原始資訊快照（機構資料與證明文件清單）→ 機構狀態 SUSPENDED →
 * 該機構所有公開中的願望加上 {@code ORG_SUSPENDED} 凍結（還沒寄出的認領暫停寄送期限）→
 * 團隊對話與 Email 通知機構、Email 通知還沒寄出的認領者。機構仍可登入、與管理員對話、修改機構資料與補文件，
 * 但不能對願望做任何操作（見 {@code SuspendedOrganizationInterceptor} 與 {@code ClaimRestriction}）。
 *
 * <p><strong>復權</strong>只解除「機構停權」這個凍結原因，其他原因（例如個資疑慮）的凍結仍然保留。
 */
@Service
public class OrganizationSuspensionService {

    private final OrganizationService organizationService;
    private final WishRepository wishes;
    private final WishFreezeService freezes;
    private final ClaimRepository claims;
    private final AttachmentService attachments;
    private final SuspensionRecordRepository records;
    private final UnlockRequestRepository unlocks;
    private final AdminTeamChatService teamChat;
    private final AdminAuditService audit;
    private final CurrentUserService currentUser;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper json;
    private final ProfileChangeLogService changeLog;

    public OrganizationSuspensionService(OrganizationService organizationService, WishRepository wishes,
                                         WishFreezeService freezes, ClaimRepository claims,
                                         AttachmentService attachments, SuspensionRecordRepository records,
                                         UnlockRequestRepository unlocks,
                                         AdminTeamChatService teamChat, AdminAuditService audit,
                                         CurrentUserService currentUser, ApplicationEventPublisher eventPublisher,
                                         ObjectMapper json, ProfileChangeLogService changeLog) {
        this.organizationService = organizationService;
        this.wishes = wishes;
        this.freezes = freezes;
        this.claims = claims;
        this.attachments = attachments;
        this.records = records;
        this.unlocks = unlocks;
        this.teamChat = teamChat;
        this.audit = audit;
        this.currentUser = currentUser;
        this.eventPublisher = eventPublisher;
        this.json = json;
        this.changeLog = changeLog;
    }

    // ---------------------------------------------------------------- 管理員

    /** 停權。只有 APPROVED 的機構能被停權。 */
    @Transactional
    public Organization suspend(UUID organizationId, ReviewReasonRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        Organization organization = organizationService.getById(organizationId);
        if (organization.getStatus() != OrganizationStatus.APPROVED) {
            throw new BusinessRuleException("ORGANIZATION_NOT_APPROVED",
                    "只有已核准的機構能被停權，目前狀態為 " + organization.getStatus());
        }
        String reason = request.note().strip();

        // 先保全證據，再改狀態：快照一定是停權當下的樣子
        records.save(new SuspensionRecord(SuspensionTargetType.ORGANIZATION, organizationId, reason,
                write(snapshotOf(organization)), admin.userId()));
        organization.suspend(admin.userId(), reason);
        audit.record(AdminAuditAction.SUSPEND_ORGANIZATION, organizationId, "理由：" + reason);

        // 該機構所有公開中的願望凍結；還沒寄出的認領暫停期限並通知認領者
        for (Wish wish : wishes.findByOrganizationIdAndStatusIn(organizationId,
                EnumSet.of(WishStatus.AVAILABLE, WishStatus.CLAIMED))) {
            freezes.freeze(wish, WishFreezeCause.ORG_SUSPENDED, "機構被停權", admin.userId())
                    .ifPresent(claim -> eventPublisher.publishEvent(new OrgSuspensionClaimEvent(claim.getId(), true)));
        }

        teamChat.reply(organizationId, new SendMessageRequest(
                "平台管理員已停權貴機構。原因：%s。停權期間你仍可登入、在這裡與管理員對話、修改機構資料與補上傳證明文件；"
                        .formatted(reason)
                        + "但無法對願望進行任何操作，也無法聯繫認領者，你的願望已凍結，認領者會看到停權說明。"
                        + "資料處理好之後，請到「機構設定」按「申請解除停權」。"));
        eventPublisher.publishEvent(new OrganizationSuspensionEvent(organizationId, true, reason));
        return organization;
    }

    /** 復權：只有已停權的機構能被復權。解除「機構停權」的凍結，恢復認領者的寄送期限。 */
    @Transactional
    public Organization reactivate(UUID organizationId, ReviewDecisionRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        Organization organization = organizationService.getById(organizationId);
        if (organization.getStatus() != OrganizationStatus.SUSPENDED) {
            throw new BusinessRuleException("ORGANIZATION_NOT_SUSPENDED",
                    "只有已停權的機構能被復權，目前狀態為 " + organization.getStatus());
        }
        String note = request == null || request.note() == null || request.note().isBlank() ? null : request.note().strip();

        organization.reactivate(admin.userId(), note);
        audit.record(AdminAuditAction.REACTIVATE_ORGANIZATION, organizationId, note == null ? "（未附註）" : "附註：" + note);
        records.findFirstByTargetTypeAndTargetIdAndLiftedAtIsNullOrderByCreatedAtDescIdDesc(
                SuspensionTargetType.ORGANIZATION, organizationId).ifPresent(record -> record.lift(admin.userId()));

        for (Wish wish : wishes.findFrozenByOrganizationSuspension(organizationId)) {
            freezes.liftCause(wish, WishFreezeCause.ORG_SUSPENDED, admin.userId())
                    .ifPresent(claim -> eventPublisher.publishEvent(new OrgSuspensionClaimEvent(claim.getId(), false)));
        }
        // 還在等待的解除申請，就此結案
        unlocks.findFirstByTargetTypeAndTargetIdAndStatus(UnlockTargetType.ORGANIZATION, organizationId, UnlockStatus.PENDING)
                .ifPresent(pending -> pending.decide(UnlockStatus.APPROVED, admin.userId(), note));

        teamChat.reply(organizationId, new SendMessageRequest(
                "平台管理員已解除貴機構的停權，願望與認領已恢復，認領者的寄送期限會順延停權的時間。"
                        + (note == null ? "" : "附註：" + note)));
        eventPublisher.publishEvent(new OrganizationSuspensionEvent(organizationId, false, null));
        return organization;
    }

    /** 管理員退回機構的解除停權申請：維持停權，機構看得到原因，可以修改資料後再申請。 */
    @Transactional
    public UnlockRequestView rejectUnlock(UUID organizationId, String note) {
        AppPrincipal admin = currentUser.requireAdmin();
        organizationService.getById(organizationId);
        UnlockRequest pending = pendingRequest(organizationId);
        pending.decide(UnlockStatus.REJECTED, admin.userId(), note.strip());
        audit.record(AdminAuditAction.DECIDE_UNLOCK_REQUEST, organizationId, "退回解除停權申請");
        teamChat.reply(organizationId, new SendMessageRequest(
                "平台管理員退回了貴機構的解除停權申請，機構仍然停權中。原因：%s。處理後可以再次申請。".formatted(note.strip())));
        return view(pending);
    }

    /**
     * 這些機構之中有待審解除停權申請的 id（管理員清單標示「申請解除」用）。
     *
     * <p>刻意只撈 id、不走 {@link #detailOf}：那支每次查看都寫一筆 VIEW_SUSPENSION_RECORD 稽核，
     * 清單每次載入都寫稽核會把紀錄洗掉，這也正是抽屜裡要用點擊載入的原因。
     */
    @Transactional(readOnly = true)
    public Set<UUID> organizationIdsWithPending(Collection<UUID> organizationIds) {
        return organizationIds.isEmpty() ? Set.of()
                : new HashSet<>(unlocks.findPendingTargetIds(UnlockTargetType.ORGANIZATION, organizationIds));
    }

    /** 管理員抽屜：停權當下保全的原始資訊、停權期間的資料修改、機構的解除申請。每次查看寫稽核。 */
    @Transactional
    public SuspensionDetailView detailOf(UUID organizationId) {
        currentUser.requireAdmin();
        Organization organization = organizationService.getById(organizationId);
        Optional<SuspensionRecord> record = records.findFirstByTargetTypeAndTargetIdAndLiftedAtIsNullOrderByCreatedAtDescIdDesc(
                SuspensionTargetType.ORGANIZATION, organizationId);
        audit.record(AdminAuditAction.VIEW_SUSPENSION_RECORD, organizationId, organization.getName());

        // 「停權之後改了什麼」：機構的資料異動現在一律記在 profile_change_logs，
        // 這裡改用時間篩選取出停權當下之後的那些。沒有進行中的停權紀錄就沒有起算點，
        // 這張卡片本來也不會顯示，給空清單即可（原本的寫法會回傳歷來全部）。
        List<SuspensionDetailView.Revision> revisionViews = record
                .map(open -> changeLog
                        .since(ProfileChangeTargetType.ORGANIZATION, organizationId, open.getCreatedAt()).stream()
                        .map(log -> new SuspensionDetailView.Revision(changeLog.read(log), log.getCreatedAt()))
                        .toList())
                .orElseGet(List::of);
        long openWishes = wishes.findByOrganizationIdAndStatusIn(organizationId,
                EnumSet.of(WishStatus.AVAILABLE, WishStatus.CLAIMED)).size();
        long affectedClaims = claims.findByOrganizationIdAndStatusIn(organizationId, activeStatuses()).size();
        return new SuspensionDetailView(
                record.map(SuspensionRecord::getReason).orElse(null),
                record.map(SuspensionRecord::getCreatedAt).orElse(null),
                record.map(r -> read(r.getSnapshot())).orElse(null),
                revisionViews,
                latestRequest(organizationId).orElse(null),
                openWishes, affectedClaims);
    }

    /**
     * 通知這間機構所有進行中認領的認領者（含已寄出的）。冒名機構的案件裡，已寄出禮物的認領者
     * 一定要有管道被告知，而管理員目前不能直接傳訊給認領者，所以用 Email。
     *
     * @return 通知的認領者人數
     */
    @Transactional
    public int notifyAffectedDonors(UUID organizationId, String message) {
        currentUser.requireAdmin();
        Organization organization = organizationService.getById(organizationId);
        List<Claim> active = claims.findByOrganizationIdAndStatusIn(organizationId, activeStatuses());
        active.forEach(claim -> eventPublisher.publishEvent(
                new com.onlinesanta.event.AffectedDonorNoticeEvent(claim.getId(), message.strip())));
        audit.record(AdminAuditAction.NOTIFY_AFFECTED_DONORS, organizationId,
                "%s，通知 %d 位認領者".formatted(organization.getName(), active.size()));
        return active.size();
    }

    // ---------------------------------------------------------------- 機構

    /** 機構申請解除停權（資料修改好、文件補上之後）。 */
    @Transactional
    public UnlockRequestView requestUnlock(String message) {
        AppPrincipal actor = currentUser.require();
        UUID organizationId = currentUser.requireOrganizationId();
        Organization organization = organizationService.getById(organizationId);
        if (organization.getStatus() != OrganizationStatus.SUSPENDED) {
            throw new BusinessRuleException("ORGANIZATION_NOT_SUSPENDED", "貴機構目前沒有被停權");
        }
        if (unlocks.findFirstByTargetTypeAndTargetIdAndStatus(
                UnlockTargetType.ORGANIZATION, organizationId, UnlockStatus.PENDING).isPresent()) {
            throw new BusinessRuleException("UNLOCK_ALREADY_REQUESTED", "已經送出申請，請等管理員審核，或先撤回再重新申請");
        }
        UnlockRequest saved = unlocks.save(new UnlockRequest(UnlockTargetType.ORGANIZATION, organizationId,
                message == null || message.isBlank() ? null : message.strip(), null, actor.userId()));
        return view(saved);
    }

    @Transactional
    public UnlockRequestView withdrawUnlock() {
        AppPrincipal actor = currentUser.require();
        UnlockRequest pending = pendingRequest(currentUser.requireOrganizationId());
        pending.decide(UnlockStatus.WITHDRAWN, actor.userId(), null);
        return view(pending);
    }

    @Transactional(readOnly = true)
    public Optional<UnlockRequestView> latestForMyOrganization() {
        return latestRequest(currentUser.requireOrganizationId());
    }

    // ---------------------------------------------------------------- 內部

    private static EnumSet<ClaimStatus> activeStatuses() {
        return EnumSet.of(ClaimStatus.CLAIMED, ClaimStatus.SHIPPED, ClaimStatus.RECEIVED);
    }

    private UnlockRequest pendingRequest(UUID organizationId) {
        return unlocks.findFirstByTargetTypeAndTargetIdAndStatus(UnlockTargetType.ORGANIZATION, organizationId, UnlockStatus.PENDING)
                .orElseThrow(() -> new BusinessRuleException("NO_PENDING_UNLOCK_REQUEST", "目前沒有待審的解除停權申請"));
    }

    private Optional<UnlockRequestView> latestRequest(UUID organizationId) {
        return unlocks.findFirstByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(UnlockTargetType.ORGANIZATION, organizationId)
                .map(this::view);
    }

    private UnlockRequestView view(UnlockRequest request) {
        return new UnlockRequestView(request.getId(), request.getStatus(), request.getMessage(), null,
                request.getDecisionNote(), request.getCreatedAt(), request.getDecidedAt());
    }

    /** 停權當下的原始資訊：機構資料與證明文件清單（檔名與時間，不含網址）。 */
    private Map<String, Object> snapshotOf(Organization organization) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", organization.getName());
        snapshot.put("contactPerson", organization.getContactPerson());
        snapshot.put("responsiblePerson", organization.getResponsiblePerson());
        snapshot.put("withholdingTaxId", organization.getWithholdingTaxId());
        snapshot.put("contactEmail", organization.getContactEmail());
        snapshot.put("contactPhone", organization.getContactPhone());
        snapshot.put("mobilePhone", organization.getMobilePhone());
        snapshot.put("address", organization.getAddress());
        snapshot.put("website", organization.getWebsite());
        snapshot.put("description", organization.getDescription());
        List<Map<String, Object>> documents = attachments.organizationDocuments(organization.getId()).stream()
                .map(this::documentOf).toList();
        snapshot.put("documents", documents);
        return snapshot;
    }

    private Map<String, Object> documentOf(AttachmentView view) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", view.id().toString());
        document.put("fileName", view.fileName());
        document.put("contentType", view.contentType());
        document.put("uploadedAt", String.valueOf(view.uploadedAt()));
        return document;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("無法序列化停權快照", e);
        }
    }

    private JsonNode read(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
