package com.onlinesanta.terms;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.terms.dto.TermsStatusView;

/**
 * 須知的同意紀錄與版本檢查。
 *
 * <p>是否「需要重新同意」只看一件事：最近一次同意的版本字串是否<strong>等於</strong>目前版本。
 * 不假設版本可以排大小——改版、回滾都會要求重新確認。
 */
@Service
public class TermsService {

    private final TermsConsentRepository consents;
    private final TermsProperties properties;
    private final CurrentUserService currentUser;

    public TermsService(TermsConsentRepository consents, TermsProperties properties,
                        CurrentUserService currentUser) {
        this.consents = consents;
        this.properties = properties;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<TermsStatusView> statusOfMine() {
        UUID userId = currentUser.require().userId();
        return List.of(TermsType.values()).stream().map(type -> status(userId, type)).toList();
    }

    @Transactional(readOnly = true)
    public TermsStatusView status(UUID userId, TermsType type) {
        String current = properties.currentVersion(type);
        return consents.findFirstByUserIdAndTermsTypeOrderByCreatedAtDesc(userId, type)
                .map(latest -> new TermsStatusView(type, type.getTitle(), current,
                        latest.getVersion(), latest.getCreatedAt(), current.equals(latest.getVersion())))
                .orElseGet(() -> new TermsStatusView(type, type.getTitle(), current, null, null, false));
    }

    /**
     * 記錄同意。{@code version} 必須是目前版本——使用者看到的是舊的前端內容時（後端已升版、
     * 網站還沒更新），不能讓他「同意」新版本。已經同意過同一版本就直接回傳現況，不重複新增。
     */
    @Transactional
    public TermsStatusView acceptMine(TermsType type, String version) {
        UUID userId = currentUser.require().userId();
        String current = properties.currentVersion(type);
        if (!current.equals(version)) {
            throw new BusinessRuleException("TERMS_VERSION_MISMATCH",
                    "須知已更新，請重新整理頁面後再閱讀並同意最新版本");
        }
        boolean alreadyAccepted = consents
                .findFirstByUserIdAndTermsTypeOrderByCreatedAtDesc(userId, type)
                .map(latest -> current.equals(latest.getVersion()))
                .orElse(false);
        if (!alreadyAccepted) {
            consents.saveAndFlush(new TermsConsent(userId, type, version));
        }
        return status(userId, type);
    }

    /** 機構送出申請前呼叫：沒有同意目前版本的《合作機構參與須知》就擋下。 */
    @Transactional(readOnly = true)
    public void requireUpToDateForOrganizationRegister(UUID userId) {
        if (!properties.enforceOnOrganizationRegister()) {
            return;
        }
        if (!status(userId, TermsType.ORG).upToDate()) {
            throw new BusinessRuleException("TERMS_NOT_ACCEPTED",
                    "請先閱讀並同意《合作機構參與須知》才能送出機構申請");
        }
    }

    /** 認領前呼叫：沒有同意目前版本就擋下。 */
    @Transactional(readOnly = true)
    public void requireUpToDateForClaim(UUID userId) {
        if (!properties.enforceOnClaim()) {
            return;
        }
        if (!status(userId, TermsType.DONOR).upToDate()) {
            throw new BusinessRuleException("TERMS_NOT_ACCEPTED",
                    "請先閱讀並同意《認領者參與須知》才能認領");
        }
    }
}
