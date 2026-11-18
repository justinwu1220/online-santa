package com.onlinesanta.closing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    List<Campaign> findAllByOrderByCollectionStartDescCreatedAtDesc();

    boolean existsByReceiptPrefix(String receiptPrefix);

    Optional<Campaign> findByReceiptPrefix(String receiptPrefix);

    /** 配號用：鎖住這一列，同時完成的兩個願望才不會拿到同一個號碼。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    Optional<Campaign> findByIdForUpdate(@Param("id") UUID id);

    /** 勸募期間涵蓋這一天、還在進行中的檔期（正常情況最多一個，由新增與修改時的重疊檢查保證）。 */
    @Query("""
            select c from Campaign c
            where c.status = com.onlinesanta.closing.CampaignStatus.ACTIVE
              and c.collectionStart <= :day and c.collectionEnd >= :day
            """)
    List<Campaign> findActiveCovering(@Param("day") LocalDate day);

    /**
     * 與指定勸募期間重疊的其他檔期數（含已結束的；{@code excludeId} 為 null 代表新增）。
     * 營運統計把每個檔期換算成一段不重疊的週期，已結束的檔期也不能重疊。
     */
    @Query("""
            select count(c) from Campaign c
            where (:excludeId is null or c.id <> :excludeId)
              and c.collectionStart <= :end and c.collectionEnd >= :start
            """)
    long countOverlapping(@Param("start") LocalDate start, @Param("end") LocalDate end,
                          @Param("excludeId") UUID excludeId);
}
