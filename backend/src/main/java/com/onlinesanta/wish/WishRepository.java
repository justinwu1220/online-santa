package com.onlinesanta.wish;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishRepository extends JpaRepository<Wish, UUID> {

    /**
     * 願望牆的主要查詢。
     *
     * <p>三個篩選條件皆可為 null 代表不篩選，用一句 JPQL 涵蓋所有組合，避免為每種
     * 組合各寫一個方法。{@code join fetch} 一次帶回機構名稱——列表要顯示機構，
     * 沒有它就會對每一筆願望各發一次查詢（N+1）。
     */
    @Query("""
            select w from Wish w
            join fetch w.organization o
            where w.status = :status
              and (:category is null or w.category = :category)
              and (:ageRange is null or w.ageRange = :ageRange)
              and (:priceRange is null or w.priceRange = :priceRange)
            """)
    Page<Wish> search(@Param("status") WishStatus status,
                      @Param("category") WishCategory category,
                      @Param("ageRange") AgeRange ageRange,
                      @Param("priceRange") PriceRange priceRange,
                      Pageable pageable);

    @EntityGraph(attributePaths = "organization")
    Optional<Wish> findWithOrganizationById(UUID id);

    @EntityGraph(attributePaths = "organization")
    Page<Wish> findByOrganizationId(UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = "organization")
    Page<Wish> findByOrganizationIdAndStatus(UUID organizationId, WishStatus status, Pageable pageable);

    // ------------------------------------------------------------ 認領的原子狀態轉換
    //
    // 這三個方法都是「條件式 UPDATE」：WHERE 子句同時帶上預期的目前狀態，回傳受影響
    // 的列數。呼叫端據此判斷操作是否成功，不需要先 SELECT 再 UPDATE（那中間有空窗）。
    //
    // clearAutomatically：原生 UPDATE 繞過了 Hibernate，持久化脈絡裡的 Wish 會過期，
    //   不清掉的話後續讀取會拿到舊值，且 flush 時會用過期的 version 而丟出樂觀鎖例外。
    // flushAutomatically：先把脈絡裡待寫入的變更送出，避免它們蓋掉這次的 UPDATE。

    /**
     * 認領：把願望從 AVAILABLE 改為 CLAIMED。
     *
     * <p><strong>這是整個搶領防超賣的核心。</strong>在 READ COMMITTED 下，這句 UPDATE
     * 會取得該列的 row lock；後到的交易會等前一筆 commit 後重讀，看到 status 已是
     * CLAIMED 而回傳 0 列。因此無論多少人同時按下認領，只有一個人會拿到 1。
     *
     * <p>相較於先 {@code SELECT ... FOR UPDATE} 再 UPDATE，這裡只需一次往返、鎖的
     * 持有時間更短，也沒有死鎖的風險。
     *
     * @return 1 表示認領成功；0 表示願望已被別人領走或已下架
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update wishes
               set status = 'CLAIMED', version = version + 1, updated_at = now()
             where id = :wishId and status = 'AVAILABLE'
            """, nativeQuery = true)
    int markClaimed(@Param("wishId") UUID wishId);

    /** 釋回或取消：願望重新上架。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update wishes
               set status = 'AVAILABLE', version = version + 1, updated_at = now()
             where id = :wishId and status = 'CLAIMED'
            """, nativeQuery = true)
    int markAvailableAgain(@Param("wishId") UUID wishId);

    /**
     * 批次釋回：一次把多個願望放回願望牆。
     *
     * <p>逾期釋回的排程掃出一批認領後，若對每筆各呼叫一次
     * {@link #markAvailableAgain}，第一次的 {@code clearAutomatically} 就會清空
     * 持久化脈絡，讓迴圈裡還沒處理的 Claim 全部變成分離狀態、後續變更再也不會寫回。
     * 因此改成所有 Claim 都改完之後，用一句 UPDATE 收尾。
     *
     * @return 實際被改動的願望數
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update wishes
               set status = 'AVAILABLE', version = version + 1, updated_at = now()
             where id in (:wishIds) and status = 'CLAIMED'
            """, nativeQuery = true)
    int markAvailableAgainAll(@Param("wishIds") Collection<UUID> wishIds);

    /** 送禮流程全部完成。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update wishes
               set status = 'FULFILLED', version = version + 1, updated_at = now()
             where id = :wishId and status = 'CLAIMED'
            """, nativeQuery = true)
    int markFulfilled(@Param("wishId") UUID wishId);
}
