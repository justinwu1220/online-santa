package com.onlinesanta.wish;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
