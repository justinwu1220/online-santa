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

    /** 這一天開放新增願望的檔期：進行中、且落在上架期間內（期間可重疊時回多個，正常最多一個）。 */
    @Query("""
            select c from Campaign c
            where c.status = com.onlinesanta.closing.CampaignStatus.ACTIVE
              and c.wishListStart <= :day and c.wishListEnd >= :day
            order by c.wishListStart
            """)
    List<Campaign> findListingOpen(@Param("day") LocalDate day);

    /** 下一個還沒開始上架的進行中檔期（顯示「下一次上架期間」用）。 */
    Optional<Campaign> findFirstByStatusAndWishListStartAfterOrderByWishListStartAsc(
            CampaignStatus status, LocalDate day);

    /** 收據歸屬：勸募開始日在這一天（含）之前、開始日最晚的檔期，不論是否進行中。 */
    Optional<Campaign> findFirstByCollectionStartLessThanEqualOrderByCollectionStartDesc(LocalDate day);

    /** 下一個檔期的勸募開始日，決定這個檔期的統計與收據歸屬範圍何時結束。 */
    Optional<Campaign> findFirstByCollectionStartGreaterThanOrderByCollectionStartAsc(LocalDate day);

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
