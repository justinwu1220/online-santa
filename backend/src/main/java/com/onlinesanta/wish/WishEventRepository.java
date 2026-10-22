package com.onlinesanta.wish;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishEventRepository extends JpaRepository<WishEvent, Long> {

    /** 遮蔽快照（個資外洩處置）：事件本身只增不改，快照欄位是唯一的例外，所以用專用的更新。 */
    // 不清持久化脈絡：呼叫端（管理員改內容）還要接著修改同一個願望，清掉會讓後面的修改變成不被追蹤
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value =
            "update wish_events set snapshot = :snapshot, redacted_at = now() where id = :id", nativeQuery = true)
    int replaceSnapshot(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("snapshot") String snapshot);


    List<WishEvent> findByWishIdOrderByCreatedAtAscIdAsc(UUID wishId);

    Optional<WishEvent> findFirstByWishIdAndEventTypeInOrderByCreatedAtDescIdDesc(
            UUID wishId, Collection<WishEventType> types);
}
