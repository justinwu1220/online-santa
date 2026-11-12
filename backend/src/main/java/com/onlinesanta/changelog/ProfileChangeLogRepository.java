package com.onlinesanta.changelog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProfileChangeLogRepository
        extends JpaRepository<ProfileChangeLog, Long>, JpaSpecificationExecutor<ProfileChangeLog> {

    /**
     * 某個對象的異動紀錄，新的在前。
     *
     * <p>一律帶 id 當第二排序鍵：created_at 來自資料庫的 {@code now()}（交易開始時間），
     * 同一個交易寫出的兩筆時間完全相同，少了 id 就沒有穩定順序，分頁會跳筆或重複。
     */
    List<ProfileChangeLog> findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(
            ProfileChangeTargetType targetType, UUID targetId);

    /**
     * 某個對象在某個時間點之後的異動，舊的在前。
     *
     * <p>停權抽屜用這支取代原本「寫入時只在停權期間才記」的做法：現在一律記錄，
     * 「停權之後改了什麼」改由時間篩選回答。
     */
    List<ProfileChangeLog> findByTargetTypeAndTargetIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAscIdAsc(
            ProfileChangeTargetType targetType, UUID targetId, Instant since);
}
