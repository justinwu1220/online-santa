package com.onlinesanta.message;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByClaimIdOrderByCreatedAtAsc(UUID claimId);

    /** 對方傳來且尚未讀取的訊息。有 idx_messages_unread 這個部分索引支撐。 */
    List<Message> findByClaimIdAndReadAtIsNullAndSenderUserIdNot(UUID claimId, UUID readerId);

    long countByClaimIdAndReadAtIsNullAndSenderUserIdNot(UUID claimId, UUID readerId);

    /**
     * 一次算出多筆認領的未讀數，供清單頁使用。
     *
     * <p>逐筆查詢會讓「我的認領」與機構後台各多出一頁筆數的查詢；這裡一次解決。
     */
    @Query("""
            select m.claimId, count(m)
            from Message m
            where m.claimId in :claimIds
              and m.readAt is null
              and m.senderUserId <> :readerId
            group by m.claimId
            """)
    List<Object[]> countUnreadByClaimIds(@Param("claimIds") Collection<UUID> claimIds,
                                         @Param("readerId") UUID readerId);
}
