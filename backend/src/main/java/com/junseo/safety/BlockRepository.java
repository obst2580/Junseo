package com.junseo.safety;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BlockRepository extends JpaRepository<Block, Block.Key> {

    /** SQL: the viewer ({@code :viewer}) and the comment's author ({@code c.author_id}) have not blocked each other. */
    String NOT_BLOCKED_WITH_AUTHOR = """
            not exists (select 1 from blocks b
                        where (b.blocker_id = :viewer and b.blocked_id = c.author_id)
                           or (b.blocker_id = c.author_id and b.blocked_id = :viewer))""";

    boolean existsByBlockerIdAndBlockedId(long blockerId, long blockedId);

    List<Block> findByBlockerIdOrderByCreatedAtDesc(long blockerId);

    @Modifying
    @Query("delete from Block b where b.blockerId = :blockerId and b.blockedId = :blockedId")
    int unblock(long blockerId, long blockedId);

    /** Which of these people blocked {@code blockedId} (they get no push for that person's messages). */
    @Query("select b.blockerId from Block b where b.blockedId = :blockedId and b.blockerId in :candidates")
    List<Long> findBlockersAmong(long blockedId, Collection<Long> candidates);
}
