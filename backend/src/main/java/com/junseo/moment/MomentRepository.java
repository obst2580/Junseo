package com.junseo.moment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Visibility rule used everywhere: the sender, or a snapshotted recipient who is still friends with the
 * sender. Friendship rows exist in both directions, so the check is a single primary-key probe.
 */
public interface MomentRepository extends JpaRepository<Moment, Long> {

    String VISIBLE_TO_VIEWER = """
            (m.sender_id = :viewer or exists (
                select 1 from moment_recipients r
                join friendships f on f.user_id = r.recipient_id and f.friend_id = m.sender_id
                where r.moment_id = m.id and r.recipient_id = :viewer))""";

    @Query(nativeQuery = true, value = "select m.* from moments m where m.id = :id and " + VISIBLE_TO_VIEWER)
    Optional<Moment> findVisible(long viewer, long id);

    @Query(nativeQuery = true, value = "select m.id from moments m where m.id in (:ids) and " + VISIBLE_TO_VIEWER)
    List<Long> findVisibleIds(long viewer, Collection<Long> ids);

    /** Feed page: my own moments plus received ones, newest first; {@code sender = 0} means everyone. */
    @Query(nativeQuery = true, value = """
            select * from (
                select m.* from moments m
                where m.sender_id = :viewer and m.id < :before and (:sender = 0 or m.sender_id = :sender)
                union all
                select m.* from moment_recipients r
                join moments m on m.id = r.moment_id
                join friendships f on f.user_id = r.recipient_id and f.friend_id = m.sender_id
                where r.recipient_id = :viewer and r.moment_id < :before and (:sender = 0 or m.sender_id = :sender)
            ) v
            order by v.id desc
            limit :limit""")
    List<Moment> findFeed(long viewer, long sender, long before, int limit);

    @Query(nativeQuery = true, value = """
            select m.* from moment_recipients r
            join moments m on m.id = r.moment_id
            join friendships f on f.user_id = r.recipient_id and f.friend_id = m.sender_id
            where r.recipient_id = :viewer
            order by r.moment_id desc
            limit 1""")
    Optional<Moment> findLatestReceived(long viewer);

    /**
     * Widget feed: still-visible received moments taken after {@code since}, newest first;
     * {@code sender = 0} means every friend. My own moments never show on my widget.
     */
    @Query(nativeQuery = true, value = """
            select m.* from moment_recipients r
            join moments m on m.id = r.moment_id
            join friendships f on f.user_id = r.recipient_id and f.friend_id = m.sender_id
            where r.recipient_id = :viewer and m.created_at > :since and (:sender = 0 or m.sender_id = :sender)
            order by r.moment_id desc
            limit :limit""")
    List<Moment> findRecentReceived(long viewer, long sender, Instant since, int limit);

    /** The newest still-visible received moment, of any age; {@code sender = 0} means every friend. */
    @Query(nativeQuery = true, value = """
            select m.* from moment_recipients r
            join moments m on m.id = r.moment_id
            join friendships f on f.user_id = r.recipient_id and f.friend_id = m.sender_id
            where r.recipient_id = :viewer and (:sender = 0 or m.sender_id = :sender)
            order by r.moment_id desc
            limit 1""")
    Optional<Moment> findLatestReceivedFrom(long viewer, long sender);

    /** Recipients who can still see the moment (excludes the sender). */
    @Query(nativeQuery = true, value = """
            select r.recipient_id from moment_recipients r
            join moments m on m.id = r.moment_id
            join friendships f on f.user_id = r.recipient_id and f.friend_id = m.sender_id
            where r.moment_id = :momentId""")
    List<Long> findCurrentRecipientIds(long momentId);

    /**
     * Recipients whose widget can show this moment: it is in one of their widget feeds (see
     * WidgetService#feed). A feed holds the newest {@code feedSize} photos taken after {@code since}, or
     * just the newest one when there are none; a one-friend feed is the same over that friend's photos, so
     * the test is "fewer than {@code feedSize} newer photos from the same friend (taken recently), or none".
     * Widget pushes for reactions and comments go only to them, so older photos don't burn the WidgetKit
     * push budget.
     */
    @Query(nativeQuery = true, value = """
            select r.recipient_id from moment_recipients r
            join moments m on m.id = r.moment_id
            join friendships f on f.user_id = r.recipient_id and f.friend_id = m.sender_id
            where r.moment_id = :momentId
              and (select count(*) from moment_recipients r2
                   join moments m2 on m2.id = r2.moment_id
                   join friendships f2 on f2.user_id = r2.recipient_id and f2.friend_id = m2.sender_id
                   where r2.recipient_id = r.recipient_id and m2.sender_id = m.sender_id and r2.moment_id > r.moment_id)
                  < case when m.created_at > :since then :feedSize else 1 end""")
    List<Long> findWidgetViewerIds(long momentId, Instant since, int feedSize);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into moment_recipients (moment_id, recipient_id)
            select :momentId, f.friend_id from friendships f where f.user_id = :senderId""")
    int snapshotRecipients(long momentId, long senderId);

    /** Like {@link #snapshotRecipients} but only the chosen friends; returns how many were friends. */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into moment_recipients (moment_id, recipient_id)
            select :momentId, f.friend_id from friendships f
            where f.user_id = :senderId and f.friend_id in (:recipientIds)""")
    int snapshotChosenRecipients(long momentId, long senderId, Collection<Long> recipientIds);
}
