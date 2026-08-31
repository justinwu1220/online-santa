package com.onlinesanta.claim;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    /** 每位民眾同時進行中的認領數上限檢查。 */
    long countByDonorIdAndStatusIn(UUID donorId, Collection<ClaimStatus> statuses);

    @EntityGraph(attributePaths = {"wish", "wish.organization", "donor"})
    Optional<Claim> findWithDetailsById(UUID id);

    /** 監控中心的跨機構檢視。 */
    @EntityGraph(attributePaths = {"wish", "wish.organization", "donor"})
    Page<Claim> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"wish", "wish.organization", "donor"})
    Page<Claim> findByStatus(ClaimStatus status, Pageable pageable);

    /** 監控中心的逾期清單（跨機構）。 */
    @EntityGraph(attributePaths = {"wish", "wish.organization", "donor"})
    @Query("""
            select c from Claim c
            where c.status = com.onlinesanta.claim.ClaimStatus.CLAIMED
              and c.shipDeadlineAt is not null
              and c.shipDeadlineAt < :now
            """)
    Page<Claim> findAllOverdue(@Param("now") Instant now, Pageable pageable);

    // ================================================================ 監控中心「全站認領」的年度篩選
    //
    // 以 claimedAt 的台北日曆年篩選，與年度統計的 cohort 口徑一致（認領的歸屬時間
    // 就是 claimedAt，不是 createdAt）。比照本分支已知的坑：不用
    // (:year is null or ...) 的 nullable-filter，Service 依「有無 year」分流成
    // 明確的方法，這裡的參數一定有值，用 >= / < 半開區間。

    @EntityGraph(attributePaths = {"wish", "wish.organization", "donor"})
    @Query("select c from Claim c where c.claimedAt >= :from and c.claimedAt < :to")
    Page<Claim> findByClaimedAtRange(@Param("from") Instant from, @Param("to") Instant to,
                                     Pageable pageable);

    @EntityGraph(attributePaths = {"wish", "wish.organization", "donor"})
    @Query("""
            select c from Claim c
             where c.status = :status
               and c.claimedAt >= :from
               and c.claimedAt < :to
            """)
    Page<Claim> findByStatusAndClaimedAtRange(@Param("status") ClaimStatus status,
                                              @Param("from") Instant from, @Param("to") Instant to,
                                              Pageable pageable);

    /** 逾期清單加年度篩選：同 {@link #findAllOverdue}，多帶 claimedAt 區間。 */
    @EntityGraph(attributePaths = {"wish", "wish.organization", "donor"})
    @Query("""
            select c from Claim c
            where c.status = com.onlinesanta.claim.ClaimStatus.CLAIMED
              and c.shipDeadlineAt is not null
              and c.shipDeadlineAt < :now
              and c.claimedAt >= :from
              and c.claimedAt < :to
            """)
    Page<Claim> findOverdueAndClaimedAtRange(@Param("now") Instant now,
                                             @Param("from") Instant from, @Param("to") Instant to,
                                             Pageable pageable);

    /** 依狀態分組計數，供監控中心的統計使用。 */
    @Query("select c.status, count(c) from Claim c group by c.status")
    List<Object[]> countByStatus();

    /** 逾期未寄送的筆數。條件與 findOverdue 一致，但不把資料撈出來。 */
    @Query("""
            select count(c) from Claim c
            where c.status = com.onlinesanta.claim.ClaimStatus.CLAIMED
              and c.shipDeadlineAt is not null
              and c.shipDeadlineAt < :now
            """)
    long countOverdue(@Param("now") Instant now);

    /** 「我的認領」清單，未篩選年度時使用。 */
    @EntityGraph(attributePaths = {"wish", "wish.organization"})
    Page<Claim> findByDonorIdOrderByClaimedAtDesc(UUID donorId, Pageable pageable);

    /**
     * 「我的認領」清單，依年度篩選。
     *
     * <p>與 {@code WishRepository.search()} 的 nullable-filter 寫法不同：那裡篩選的是
     * enum 欄位，這裡篩選的是 {@code Instant}。實測 {@code (:from is null or
     * c.claimedAt >= :from)} 這種寫法在 Instant 參數上會讓 Postgres 回
     * 「could not determine data type of parameter」——enum 有 Hibernate 明確的型別描述，
     * 就算該次呼叫綁的是 null 也推得出型別；Instant 在只出現於 {@code is null} 判斷式
     * 的那個 bind 位置卻推不出來。因此拆成兩個方法，年度篩選與否在 Service 層分流，
     * 兩邊的參數都一定有值，不會踩到這個問題。
     */
    @EntityGraph(attributePaths = {"wish", "wish.organization"})
    @Query("""
            select c from Claim c
            where c.donor.id = :donorId
              and c.claimedAt >= :from
              and c.claimedAt < :to
            order by c.claimedAt desc
            """)
    Page<Claim> findByDonorIdAndClaimedAtBetween(@Param("donorId") UUID donorId,
                                                 @Param("from") Instant from,
                                                 @Param("to") Instant to,
                                                 Pageable pageable);

    @EntityGraph(attributePaths = {"wish", "donor"})
    @Query("select c from Claim c where c.wish.organization.id = :organizationId")
    Page<Claim> findByOrganizationId(@Param("organizationId") UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"wish", "donor"})
    @Query("select c from Claim c where c.wish.organization.id = :organizationId and c.status = :status")
    Page<Claim> findByOrganizationIdAndStatus(@Param("organizationId") UUID organizationId,
                                              @Param("status") ClaimStatus status,
                                              Pageable pageable);

    /**
     * 機構後台的逾期提醒：手動釋回政策的機構靠這個清單自行決定要不要收回。
     */
    @EntityGraph(attributePaths = {"wish", "donor"})
    @Query("""
            select c from Claim c
            where c.wish.organization.id = :organizationId
              and c.status = com.onlinesanta.claim.ClaimStatus.CLAIMED
              and c.shipDeadlineAt is not null
              and c.shipDeadlineAt < :now
            """)
    Page<Claim> findOverdueByOrganizationId(@Param("organizationId") UUID organizationId,
                                            @Param("now") Instant now,
                                            Pageable pageable);

    /**
     * 逾期未寄送的認領，供釋回排程使用。
     *
     * <p>只掃 CLAIMED：已寄出的不算逾期。有 idx_claims_overdue 這個部分索引支撐。
     */
    @Query("""
            select c from Claim c
            join fetch c.wish w
            join fetch w.organization
            where c.status = com.onlinesanta.claim.ClaimStatus.CLAIMED
              and c.shipDeadlineAt is not null
              and c.shipDeadlineAt < :now
            """)
    List<Claim> findOverdue(@Param("now") Instant now);

    // ================================================================ 年度回顧
    //
    // cohort 制：一律以 claimedAt 落在 [from, to) 判定該筆認領屬於哪個年度，完成／釋回／
    // 取消都歸屬認領當年，不用 completedAt 篩選年度（見 TaiwanYear 的說明）。

    /** 捐贈者最早一筆認領的時間，供年度下拉選單推導可選年份。 */
    @Query("select min(c.claimedAt) from Claim c where c.donor.id = :donorId")
    Instant earliestClaimedAtByDonor(@Param("donorId") UUID donorId);

    /**
     * 捐贈者年度小結。單句聚合查詢一次算出四個數字，避免逐一往返：
     * {@code [認領數, 完成數, 送禮孩子數(distinct wish), 支持機構數(distinct organization)]}。
     *
     * <p>{@code count(case when ... then 1 end)} 而非 {@code then c end}：CASE 的結果只能是
     * 純量，把整個實體 {@code c} 放進去會被 Hibernate 當成要對實體做基本值型別轉換，丟出
     * {@code ClassCastException}（{@code SingleTableEntityPersister} 不是
     * {@code BasicValuedMapping}）。
     *
     * <p>送禮孩子數與支持機構數只計已完成的認領——取消或釋回的認領根本沒有把禮物送出去，
     * 不該算進「送禮」或「支持」。{@code count(distinct case when ... then x end)}：
     * 未完成的列該 CASE 求值為 null，{@code count(distinct ...)} 本就會忽略 null，
     * 不需要額外的 filter 子句。
     */
    @Query("""
            select count(c),
                   count(case when c.status = com.onlinesanta.claim.ClaimStatus.COMPLETED then 1 end),
                   count(distinct case when c.status = com.onlinesanta.claim.ClaimStatus.COMPLETED
                                        then c.wish.id end),
                   count(distinct case when c.status = com.onlinesanta.claim.ClaimStatus.COMPLETED
                                        then c.wish.organization.id end)
              from Claim c
             where c.donor.id = :donorId
               and c.claimedAt >= :from
               and c.claimedAt < :to
            """)
    List<Object[]> donorAnnualAggregate(@Param("donorId") UUID donorId,
                                        @Param("from") Instant from, @Param("to") Instant to);

    /** 機構最早一筆認領的時間（依願望所屬機構），供年度下拉選單推導可選年份。 */
    @Query("select min(c.claimedAt) from Claim c where c.wish.organization.id = :organizationId")
    Instant earliestClaimedAtByOrganization(@Param("organizationId") UUID organizationId);

    /**
     * 機構年度認領狀態分布：{@code [認領數, 完成數, 釋回數, 取消數]}。
     *
     * <p>釋回數涵蓋機構手動收回與逾期自動釋回兩種——兩者合計才是「不再進行中」的釋回
     * 總量；自動釋回的細項另見 {@link ClaimEventRepository#countAutoReleasedForOrganization}。
     */
    @Query("""
            select count(c),
                   count(case when c.status = com.onlinesanta.claim.ClaimStatus.COMPLETED then 1 end),
                   count(case when c.status = com.onlinesanta.claim.ClaimStatus.RELEASED then 1 end),
                   count(case when c.status = com.onlinesanta.claim.ClaimStatus.CANCELLED then 1 end)
              from Claim c
             where c.wish.organization.id = :organizationId
               and c.claimedAt >= :from
               and c.claimedAt < :to
            """)
    List<Object[]> organizationAnnualStatusAggregate(@Param("organizationId") UUID organizationId,
                                                     @Param("from") Instant from, @Param("to") Instant to);

    /**
     * 跨年完成：該年度認領、但完成時間落在隔年（或更晚）的筆數。
     *
     * <p>量化聖誕檔期作業拖過年末的規模——{@code completedAt >= to} 代表完成時已不在
     * 認領當年的日曆年內（{@code to} 是認領年度的隔年起點）。
     */
    @Query("""
            select count(c) from Claim c
             where c.wish.organization.id = :organizationId
               and c.claimedAt >= :from
               and c.claimedAt < :to
               and c.status = com.onlinesanta.claim.ClaimStatus.COMPLETED
               and c.completedAt >= :to
            """)
    long countCrossYearCompletions(@Param("organizationId") UUID organizationId,
                                   @Param("from") Instant from, @Param("to") Instant to);

    /**
     * 平均完成天數（{@code claimedAt → completedAt}，僅計入已完成的認領）。
     *
     * <p>用原生 SQL：JPQL 沒有可攜的時間戳相減語法，且這裡只是單一數值的聚合，
     * 不需要 Hibernate 的物件對應。
     */
    @Query(value = """
            select avg(extract(epoch from (c.completed_at - c.claimed_at)) / 86400.0)
              from claims c
              join wishes w on w.id = c.wish_id
             where w.organization_id = :organizationId
               and c.claimed_at >= :from
               and c.claimed_at < :to
               and c.status = 'COMPLETED'
            """, nativeQuery = true)
    Double averageCompletionDaysForOrganization(@Param("organizationId") UUID organizationId,
                                                @Param("from") Instant from, @Param("to") Instant to);

    /**
     * 機構的每月認領分布，月份為台北時區的月份（1–12）。
     *
     * <p>用原生 SQL 的 {@code AT TIME ZONE} 換算——JPQL 表達不了時區轉換。直接在 SQL 裡
     * 算出月份數字，而非用 {@code date_trunc} 回傳時間戳：後者經 JDBC 映射回
     * {@code java.sql.Timestamp} 時容易被 JVM 預設時區再轉一次，多繞一手時區換算，
     * 在此不需要月份以外的資訊，直接算出整數最不容易出錯。
     */
    @Query(value = """
            select extract(month from (c.claimed_at AT TIME ZONE 'Asia/Taipei'))::int as month,
                   count(*)
              from claims c
              join wishes w on w.id = c.wish_id
             where w.organization_id = :organizationId
               and c.claimed_at >= :from
               and c.claimed_at < :to
             group by month
            """, nativeQuery = true)
    List<Object[]> monthlyClaimsForOrganization(@Param("organizationId") UUID organizationId,
                                                @Param("from") Instant from, @Param("to") Instant to);

    /**
     * 機構單月的每日認領分布，日期為台北時區的日期（1–月底）。供「每月分布」長條圖
     * 點選某月後的下鑽使用，寫法比照 {@link #monthlyClaimsForOrganization}。
     */
    @Query(value = """
            select extract(day from (c.claimed_at AT TIME ZONE 'Asia/Taipei'))::int as day,
                   count(*)
              from claims c
              join wishes w on w.id = c.wish_id
             where w.organization_id = :organizationId
               and c.claimed_at >= :from
               and c.claimed_at < :to
             group by day
            """, nativeQuery = true)
    List<Object[]> dailyClaimsForOrganization(@Param("organizationId") UUID organizationId,
                                              @Param("from") Instant from, @Param("to") Instant to);

    /** 平台最早一筆認領的時間，供管理端年度下拉選單推導可選年份。 */
    @Query("select min(c.claimedAt) from Claim c")
    Instant earliestClaimedAtPlatformWide();

    /** 該年度至少認領一次的 distinct 捐贈者數，即監控中心的「活躍捐贈者」。 */
    @Query("""
            select count(distinct c.donor.id) from Claim c
             where c.claimedAt >= :from and c.claimedAt < :to
            """)
    long countDistinctActiveDonors(@Param("from") Instant from, @Param("to") Instant to);

    /** 平台年度認領總覽：{@code [認領數, 完成數]}。 */
    @Query("""
            select count(c),
                   count(case when c.status = com.onlinesanta.claim.ClaimStatus.COMPLETED then 1 end)
              from Claim c
             where c.claimedAt >= :from and c.claimedAt < :to
            """)
    List<Object[]> platformAnnualClaimAggregate(@Param("from") Instant from, @Param("to") Instant to);

    /** 平台年度的每月認領趨勢，月份為台北時區的月份（1–12）。 */
    @Query(value = """
            select extract(month from (claimed_at AT TIME ZONE 'Asia/Taipei'))::int as month,
                   count(*)
              from claims
             where claimed_at >= :from and claimed_at < :to
             group by month
            """, nativeQuery = true)
    List<Object[]> monthlyClaimsPlatformWide(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 平台單月的每日認領分布，日期為台北時區的日期（1–月底）。供「每月趨勢」長條圖
     * 點選某月後的下鑽使用，寫法比照 {@link #monthlyClaimsPlatformWide}。
     */
    @Query(value = """
            select extract(day from (claimed_at AT TIME ZONE 'Asia/Taipei'))::int as day,
                   count(*)
              from claims
             where claimed_at >= :from and claimed_at < :to
             group by day
            """, nativeQuery = true)
    List<Object[]> dailyClaimsPlatformWide(@Param("from") Instant from, @Param("to") Instant to);

    /** 平台年度的認領結果分布，只涵蓋三種終局狀態（完成／釋回／取消）。 */
    @Query("""
            select c.status, count(c) from Claim c
             where c.claimedAt >= :from and c.claimedAt < :to
               and c.status in (com.onlinesanta.claim.ClaimStatus.COMPLETED,
                                com.onlinesanta.claim.ClaimStatus.RELEASED,
                                com.onlinesanta.claim.ClaimStatus.CANCELLED)
             group by c.status
            """)
    List<Object[]> claimOutcomeCountsPlatformWide(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 該年度完成認領數前五名的機構：{@code [機構 id, 機構名稱, 完成數]}。
     *
     * <p>僅管理端使用——機構彼此看不到對方的數字，這條查詢只從
     * {@code AdminAnnualStatsService} 呼叫。
     */
    @Query("""
            select w.organization.id, w.organization.name, count(c)
              from Claim c join c.wish w
             where c.claimedAt >= :from and c.claimedAt < :to
               and c.status = com.onlinesanta.claim.ClaimStatus.COMPLETED
             group by w.organization.id, w.organization.name
             order by count(c) desc
            """)
    List<Object[]> topOrganizationsByCompletedClaims(@Param("from") Instant from, @Param("to") Instant to,
                                                      Pageable pageable);
}
