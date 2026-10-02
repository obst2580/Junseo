package com.junseo.group;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {

    Optional<GroupMessage> findBySenderIdAndClientId(long senderId, String clientId);

    @Query(nativeQuery = true, value = """
            select * from group_messages
            where group_id = :groupId and id < :before
            order by id desc
            limit :limit""")
    List<GroupMessage> findPage(long groupId, long before, int limit);

    /** The newest message of each group (groups without messages are left out). */
    @Query(nativeQuery = true, value = """
            select distinct on (group_id) * from group_messages
            where group_id in (:groupIds)
            order by group_id, id desc""")
    List<GroupMessage> findLatest(Collection<Long> groupIds);

    @Query("select max(m.id) from GroupMessage m where m.groupId = :groupId")
    Optional<Long> findLatestId(long groupId);
}
