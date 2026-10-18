package com.onlinesanta.terms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 須知相關設定（{@code app.terms.*}）。
 *
 * @param donorVersion   目前《認領者參與須知》的版本識別。改版時改這裡，並同步前端
 *                       {@code DONOR_TERMS.version}；使用者最新一筆同意的版本與它不同，
 *                       就必須重新同意
 * @param orgVersion     目前《合作機構參與須知》的版本識別，改版時同步前端 {@code ORG_TERMS.version}
 * @param enforceOnClaim 認領前是否要求已同意目前版本。預設開；只有測試關閉，
 *                       免得每個認領相關的測試都要先補一筆同意
 * @param enforceOnOrganizationRegister 機構送出申請前是否要求已同意目前版本的機構須知。
 *                       預設開；理由同上
 */
@ConfigurationProperties(prefix = "app.terms")
public record TermsProperties(String donorVersion, String orgVersion, boolean enforceOnClaim,
                              boolean enforceOnOrganizationRegister) {

    public String currentVersion(TermsType type) {
        return switch (type) {
            case DONOR -> donorVersion;
            case ORG -> orgVersion;
        };
    }
}
