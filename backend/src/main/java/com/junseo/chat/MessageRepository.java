package com.junseo.chat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findBySenderIdAndClientId(long senderId, String clientId);

    /** Uses the (least, greatest) expression index so both directions of a thread are one range scan. */
    @Query(nativeQuery = true, value = """
            select * from messages
            where least(sender_id, receiver_id) = :low and greatest(sender_id, receiver_id) = :high and id < :before
            order by id desc
            limit :limit""")
    List<Message> findThread(long low, long high, long before, int limit);

    /** Rows of {@code [peerId, lastMessageId, unreadCount]}, most recent conversation first. */
    @Query(nativeQuery = true, value = """
            select l.peer_id, l.last_id,
                   (select count(*) from messages u
                    where u.sender_id = l.peer_id and u.receiver_id = :me and u.read_at is null) as unread
            from (
                select case when sender_id = :me then receiver_id else sender_id end as peer_id, max(id) as last_id
                from messages
                where sender_id = :me or receiver_id = :me
                group by 1
            ) l
            order by l.last_id desc""")
    List<Object[]> findConversations(long me);

    @Modifying
    @Query("""
            update Message m set m.readAt = :now
            where m.senderId = :peerId and m.receiverId = :me and m.readAt is null""")
    int markRead(long me, long peerId, Instant now);
}
