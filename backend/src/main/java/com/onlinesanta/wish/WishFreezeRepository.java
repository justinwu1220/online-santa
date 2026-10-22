package com.onlinesanta.wish;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishFreezeRepository extends JpaRepository<WishFreeze, Long> {

    List<WishFreeze> findByWishIdAndLiftedAtIsNullOrderByCreatedAtAsc(UUID wishId);

    List<WishFreeze> findByWishIdOrderByCreatedAtAsc(UUID wishId);
}
