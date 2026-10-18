package com.onlinesanta.message;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * 認領內的對話：使用者訊息與認領當下的招呼。系統通知（SYSTEM）只在機構的願望對話裡出現，
     * 認領者看不到。
     */
    List<Message> findByClaimIdAndKindInOrderByCreatedAtAscIdAsc(UUID claimId, Collection<MessageKind> kinds);

    /**
     * 機構看一個願望的對話：該願望所有的系統通知，加上目前這筆認領的使用者訊息與招呼。
     * 以前的認領（已收回或取消）的使用者訊息不在這裡，它們屬於那筆已經結束的認領。
     * {@code claimId} 為 null（還沒有人認領）時只會有系統通知。
     */
    @Query("""
            select m from Message m
            where m.wishId = :wishId
              and (m.kind = com.onlinesanta.message.MessageKind.SYSTEM
                   or (m.claimId is not null and m.claimId = :claimId))
            order by m.createdAt asc, m.id asc
            """)
    List<Message> findWishConversation(@Param("wishId") UUID wishId, @Param("claimId") UUID claimId);

    /** 願望上機構還沒讀的系統通知。 */
    List<Message> findByWishIdAndKindAndReadAtIsNull(UUID wishId, MessageKind kind);

    /**
     * 機構一個願望的未讀數：系統通知，加上目前認領者傳來的訊息（不是自己這邊的人傳的）。
     */
    @Query("""
            select count(m) from Message m
            where m.wishId = :wishId and m.readAt is null
              and (m.kind = com.onlinesanta.message.MessageKind.SYSTEM
                   or (m.kind = com.onlinesanta.message.MessageKind.USER
                       and m.claimId = :claimId and m.senderUserId <> :readerId))
            """)
    long countWishUnread(@Param("wishId") UUID wishId, @Param("claimId") UUID claimId,
                         @Param("readerId") UUID readerId);

    /** 機構所有願望的未讀總數（側邊選單的紅色數字）。 */
    @Query("""
            select count(m) from Message m
            where m.readAt is null
              and ((m.kind = com.onlinesanta.message.MessageKind.SYSTEM
                    and m.wishId in (select w.id from Wish w where w.organization.id = :organizationId))
                   or (m.kind = com.onlinesanta.message.MessageKind.USER
                       and m.senderUserId <> :readerId
                       and m.claimId in (select c.id from Claim c
                                         where c.wish.organization.id = :organizationId
                                           and c.status in :occupying)))
            """)
    long countOrganizationUnread(@Param("organizationId") UUID organizationId,
                                 @Param("readerId") UUID readerId,
                                 @Param("occupying") Collection<com.onlinesanta.claim.ClaimStatus> occupying);

    /** 對方傳來且尚未讀取的訊息。有 idx_messages_unread 這個部分索引支撐。 */
    List<Message> findByClaimIdAndReadAtIsNullAndSenderUserIdNot(UUID claimId, UUID readerId);

    long countByClaimIdAndReadAtIsNullAndSenderUserIdNot(UUID claimId, UUID readerId);

    /**
     * 這個寄件人先前寄出、對方還沒讀的訊息數。供新訊息通知信的防轟炸節流使用：
     * 對方已經有這個人寄的舊訊息還沒讀，就不用再寄一封信提醒——等對方讀了（或
     * 這個人的訊息被讀過）,下一則新訊息才會再次觸發通知。
     */
    long countByClaimIdAndReadAtIsNullAndSenderUserId(UUID claimId, UUID senderUserId);

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
