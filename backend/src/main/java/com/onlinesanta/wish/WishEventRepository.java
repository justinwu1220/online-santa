package com.onlinesanta.wish;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishEventRepository extends JpaRepository<WishEvent, Long> {

    List<WishEvent> findByWishIdOrderByCreatedAtAscIdAsc(UUID wishId);

    Optional<WishEvent> findFirstByWishIdAndEventTypeInOrderByCreatedAtDescIdDesc(
            UUID wishId, Collection<WishEventType> types);
}
