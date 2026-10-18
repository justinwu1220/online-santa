package com.onlinesanta.terms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 須知相關設定（{@code app.terms.*}）。
 *
 * @param donorVersion   目前《認領者參與須知》的版本識別。改版時改這裡，並同步前端
 *                       {@code DONOR_TERMS.version}；使用者最新一筆同意的版本與它不同，
 *                       就必須重新同意
 * @param enforceOnClaim 認領前是否要求已同意目前版本。預設開；只有測試關閉，
 *                       免得每個認領相關的測試都要先補一筆同意
 */
@ConfigurationProperties(prefix = "app.terms")
public record TermsProperties(String donorVersion, boolean enforceOnClaim) {

    public String currentVersion(TermsType type) {
        return switch (type) {
            case DONOR -> donorVersion;
        };
    }
}
