package com.onlinesanta.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.onlinesanta.changelog.ProfileChangeLog;
import com.onlinesanta.changelog.ProfileChangeSource;
import com.onlinesanta.changelog.ProfileChangeTargetType;
import com.onlinesanta.common.dto.FieldChange;
import com.onlinesanta.user.UserRole;

/**
 * 管理員看到的一筆個資異動紀錄。
 *
 * @param targetLabel 對象的可讀名稱（認領者的 email 或機構名稱）；對象已被刪除時為 null，
 *                    前端顯示 id。刻意不在寫入時去正規化保存——操作者的 email 必須永遠
 *                    指得出人所以要存，對象則由 id 加上異動內容本身（含姓名）辨識
 * @param actorEmail  操作者在「異動當下」的 email，直接取自紀錄，不是現在查出來的
 * @param changes     有變動的欄位（機器欄位名，中文標籤由前端翻）
 */
public record ProfileChangeView(
        Long id,
        ProfileChangeTargetType targetType,
        UUID targetId,
        String targetLabel,
        List<FieldChange> changes,
        UUID actorUserId,
        String actorEmail,
        UserRole actorRole,
        ProfileChangeSource source,
        Instant occurredAt) {

    public static ProfileChangeView from(ProfileChangeLog log, String targetLabel, List<FieldChange> changes) {
        return new ProfileChangeView(
                log.getId(), log.getTargetType(), log.getTargetId(), targetLabel, changes,
                log.getActorUserId(), log.getActorEmail(), log.getActorRole(),
                log.getSource(), log.getCreatedAt());
    }
}
