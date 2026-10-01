package com.junseo.group;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMember.Key> {

    boolean existsByGroupIdAndUserId(long groupId, long userId);

    List<GroupMember> findByGroupId(long groupId);

    List<GroupMember> findByGroupIdIn(Collection<Long> groupIds);

    @Query("select m.groupId from GroupMember m where m.userId = :userId")
    List<Long> findGroupIds(long userId);

    long countByGroupId(long groupId);

    /** Never moves backwards, so a stale read request cannot resurrect unread messages. */
    @Modifying
    @Query(nativeQuery = true, value = """
            update chat_group_members set last_read_id = greatest(last_read_id, :messageId)
            where group_id = :groupId and user_id = :userId""")
    int advanceRead(long groupId, long userId, long messageId);

    @Modifying
    @Query("delete from GroupMember m where m.groupId = :groupId and m.userId = :userId")
    int leave(long groupId, long userId);

    /**
     * Rows of {@code [groupId, unreadCount]} for every group the user is in: messages from others newer
     * than what the user has read.
     */
    @Query(nativeQuery = true, value = """
            select gm.group_id,
                   (select count(*) from group_messages m
                    where m.group_id = gm.group_id and m.id > gm.last_read_id and m.sender_id <> gm.user_id) as unread
            from chat_group_members gm
            where gm.user_id = :userId""")
    List<Object[]> findUnreadCounts(long userId);
}
