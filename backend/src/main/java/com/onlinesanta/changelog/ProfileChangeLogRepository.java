package com.onlinesanta.changelog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 這個對象別之中「有異動紀錄」的對象，最近改過的在前。
     *
     * <p>用 group by 而不是把所有人列出來再過濾：清單要回答的是「哪些人改過資料」，
     * 沒有紀錄的對象不該出現，否則真正要看的那幾筆會被淹掉。
     *
     * <p>排序帶 targetId 收尾：同一毫秒的 max(created_at) 沒有第二鍵就沒有穩定順序，
     * 分頁會跳筆（created_at 來自 now()，是交易時間）。
     */
    @Query("""
            select new com.onlinesanta.changelog.ProfileChangeTargetSummary(
                       log.targetType, log.targetId, count(log), max(log.createdAt))
              from ProfileChangeLog log
             where log.targetType = :targetType
             group by log.targetType, log.targetId
             order by max(log.createdAt) desc, log.targetId desc
            """)
    Page<ProfileChangeTargetSummary> findTargets(@Param("targetType") ProfileChangeTargetType targetType,
                                                 Pageable pageable);
}
