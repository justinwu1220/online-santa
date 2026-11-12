package com.onlinesanta.changelog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

/**
 * 管理員查閱個資異動紀錄的篩選條件。
 *
 * <p>比照 {@link com.onlinesanta.claim.ClaimSpecifications}：維度可以任意組合，各寫一支
 * repository 方法會隨維度翻倍；而且 Instant 參數用 {@code (:from is null or ...)} 在
 * Postgres 推不出型別，Specification 只在有篩選時才加條件，沒有這個問題。
 *
 * <p>時間區間是半開的 {@code [from, to)}。
 */
public final class ProfileChangeSpecifications {

    private ProfileChangeSpecifications() {
    }

    /**
     * @param targetType null 代表不分對象別
     * @param targetId   null 代表不限定單一對象；有值時 targetType 必須一起給
     * @param from       起點（含），可為 null
     * @param to         終點（不含），可為 null
     */
    public static Specification<ProfileChangeLog> forAdminList(ProfileChangeTargetType targetType, UUID targetId,
                                                               Instant from, Instant to) {
        List<Specification<ProfileChangeLog>> parts = new ArrayList<>();

        if (targetType != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("targetType"), targetType));
        }
        if (targetId != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("targetId"), targetId));
        }
        if (from != null) {
            parts.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            parts.add((root, query, cb) -> cb.lessThan(root.get("createdAt"), to));
        }

        return Specification.allOf(parts);
    }
}
