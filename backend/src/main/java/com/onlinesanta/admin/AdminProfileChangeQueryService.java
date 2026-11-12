package com.onlinesanta.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.admin.dto.ProfileChangeTargetView;
import com.onlinesanta.admin.dto.ProfileChangeView;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.changelog.ProfileChangeLog;
import com.onlinesanta.changelog.ProfileChangeLogRepository;
import com.onlinesanta.changelog.ProfileChangeLogService;
import com.onlinesanta.changelog.ProfileChangeSpecifications;
import com.onlinesanta.changelog.ProfileChangeTargetSummary;
import com.onlinesanta.changelog.ProfileChangeTargetType;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

/**
 * 管理員查閱個資異動紀錄。
 *
 * <p>這支會讓管理員看到任意他人的新舊真實姓名、電話、地址、統編，比已經會留痕的
 * VIEW_CLAIM_DETAIL 更敏感，所以<strong>查閱本身也寫稽核</strong>——否則這套機制防得了
 * 機構，卻防不了內部人。一個請求寫一列，不是每筆資料一列。
 */
@Service
public class AdminProfileChangeQueryService {

    /** 一個請求最多倒出幾列。沒有上限的話，一次請求就能把整張個資表拉走。 */
    private static final int MAX_PAGE_SIZE = 100;

    private final ProfileChangeLogRepository logs;
    private final ProfileChangeLogService changeLog;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final AdminAuditService audit;
    private final CurrentUserService currentUser;

    public AdminProfileChangeQueryService(ProfileChangeLogRepository logs, ProfileChangeLogService changeLog,
                                          UserRepository users, OrganizationRepository organizations,
                                          AdminAuditService audit, CurrentUserService currentUser) {
        this.logs = logs;
        this.changeLog = changeLog;
        this.users = users;
        this.organizations = organizations;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    /**
     * 有異動紀錄的對象清單（沒有紀錄的不會出現）。
     *
     * <p>不寫稽核：這份清單只有對象名稱、次數與時間，沒有任何欄位值——與同樣會列出
     * 信箱與真實姓名、也不寫稽核的管理員認領者清單一致。看實際改了什麼要開抽屜，
     * 那一步才寫。這也避免了切換分頁、換頁就灌爆稽核紀錄。
     */
    @Transactional(readOnly = true)
    public Page<ProfileChangeTargetView> targets(ProfileChangeTargetType targetType, int page, int size) {
        currentUser.requireAdmin();

        Page<ProfileChangeTargetSummary> found = logs.findTargets(targetType, pageRequest(page, size, null));
        Map<UUID, String> labels = labels(targetType,
                found.getContent().stream().map(ProfileChangeTargetSummary::targetId).collect(Collectors.toSet()));
        return found.map(summary -> ProfileChangeTargetView.from(summary, labels.get(summary.targetId())));
    }

    /**
     * @param targetType null 代表不分對象別
     * @param targetId   null 代表不限定單一對象
     * @param from       起日（含），台灣時區
     * @param to         迄日（含），台灣時區；轉成半開區間時會加一天
     * @param page       第幾頁（0 起算）
     * @param size       每頁幾筆，上限 {@value #MAX_PAGE_SIZE}
     */
    @Transactional
    public Page<ProfileChangeView> list(ProfileChangeTargetType targetType, UUID targetId,
                                        LocalDate from, LocalDate to, int page, int size) {
        currentUser.requireAdmin();

        Instant fromAt = from == null ? null : from.atStartOfDay(TaiwanYear.ZONE).toInstant();
        Instant toAt = to == null ? null : to.plusDays(1).atStartOfDay(TaiwanYear.ZONE).toInstant();

        Page<ProfileChangeLog> found = logs.findAll(
                ProfileChangeSpecifications.forAdminList(targetType, targetId, fromAt, toAt),
                pageRequest(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        recordAudit(targetType, targetId, from, to, found.getTotalElements());

        Map<UUID, String> labels = labelsFor(found.getContent());
        return found.map(log -> ProfileChangeView.from(
                log, labels.get(log.getTargetId()), changeLog.read(log)));
    }

    /**
     * 排序固定在伺服器端，不吃 client 的 {@code ?sort=}：讓外部改動一份證據檢視的排序並
     * 不合理，而且 created_at 是交易時間，少了第二鍵分頁會跳筆。
     *
     * @param sort null 代表排序寫在查詢裡（對象清單那支）
     */
    private static PageRequest pageRequest(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return sort == null ? PageRequest.of(safePage, safeSize) : PageRequest.of(safePage, safeSize, sort);
    }

    /**
     * 一次把這一頁涉及的對象名稱撈齊，不要逐列查（比照
     * {@link AdminOverviewController} 的稽核清單）。
     */
    private Map<UUID, String> labelsFor(List<ProfileChangeLog> page) {
        Map<ProfileChangeTargetType, Set<UUID>> byType = page.stream().collect(
                Collectors.groupingBy(ProfileChangeLog::getTargetType,
                        Collectors.mapping(ProfileChangeLog::getTargetId, Collectors.toSet())));

        Map<UUID, String> labels = new java.util.HashMap<>();
        byType.forEach((type, ids) -> labels.putAll(labels(type, ids)));
        return labels;
    }

    /** 單一對象別的名稱批次查詢。 */
    private Map<UUID, String> labels(ProfileChangeTargetType targetType, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        if (targetType == ProfileChangeTargetType.USER) {
            return users.findByIdIn(List.copyOf(ids)).stream()
                    .collect(Collectors.toMap(User::getId, User::getEmail, (first, second) -> first));
        }
        return organizations.findAllById(ids).stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getName, (first, second) -> first));
    }

    /**
     * 查閱留痕。分三個動作值是因為 {@link AdminAuditAction#targetType()} 是靜態的
     * action→type 對應，同一個值不可能既是 USER 又是 ORGANIZATION；保留兩個對象別的值，
     * 才留得住「這筆資料被誰看過」的反查。
     */
    private void recordAudit(ProfileChangeTargetType targetType, UUID targetId,
                             LocalDate from, LocalDate to, long total) {
        String range = (from == null && to == null) ? "全部期間"
                : (from == null ? "" : from.toString()) + "~" + (to == null ? "" : to.toString());
        String detail = "%s，%s，%d 筆".formatted(
                targetId == null ? describe(targetType) : targetId.toString(), range, total);

        if (targetId != null && targetType == ProfileChangeTargetType.USER) {
            audit.record(AdminAuditAction.VIEW_USER_PROFILE_CHANGES, targetId, detail);
        } else if (targetId != null && targetType == ProfileChangeTargetType.ORGANIZATION) {
            audit.record(AdminAuditAction.VIEW_ORGANIZATION_PROFILE_CHANGES, targetId, detail);
        } else {
            audit.record(AdminAuditAction.VIEW_PROFILE_CHANGE_LOG, null, detail);
        }
    }

    private static String describe(ProfileChangeTargetType targetType) {
        if (targetType == null) {
            return "全部對象";
        }
        return targetType == ProfileChangeTargetType.USER ? "全部認領者" : "全部機構";
    }
}
