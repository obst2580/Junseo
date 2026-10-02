package com.junseo.comment;

import com.junseo.safety.BlockRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Like reactions, comments are shown only while their author can still see the moment, and never between people
 * who blocked each other ({@code viewer} is who is looking).
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    String AUTHOR_CAN_SEE = "(" + """
            (c.author_id = m.sender_id or exists (
                select 1 from friendships f where f.user_id = c.author_id and f.friend_id = m.sender_id))"""
            + " and " + BlockRepository.NOT_BLOCKED_WITH_AUTHOR + ")";

    @Query(nativeQuery = true, value = """
            select c.* from comments c join moments m on m.id = c.moment_id
            where c.moment_id = :momentId and """ + AUTHOR_CAN_SEE + " order by c.id")
    List<Comment> findVisibleByMomentId(long momentId, long viewer);

    /** The newest {@code perMoment} visible comments of each moment, oldest first. */
    @Query(nativeQuery = true, value = """
            select c.* from comments c where c.id in (
                select x.id from (
                    select c.id, row_number() over (partition by c.moment_id order by c.id desc) as rn
                    from comments c join moments m on m.id = c.moment_id
                    where c.moment_id in (:momentIds) and """ + AUTHOR_CAN_SEE + """
                ) x where x.rn <= :perMoment)
            order by c.id""")
    List<Comment> findRecentVisible(Collection<Long> momentIds, int perMoment, long viewer);

    /** Rows of {@code [momentId, count]}. */
    @Query(nativeQuery = true, value = """
            select c.moment_id, count(*) from comments c join moments m on m.id = c.moment_id
            where c.moment_id in (:momentIds) and """ + AUTHOR_CAN_SEE + " group by c.moment_id")
    List<Object[]> countVisibleByMomentIds(Collection<Long> momentIds, long viewer);
}
