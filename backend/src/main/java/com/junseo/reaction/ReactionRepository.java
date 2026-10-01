package com.junseo.reaction;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    /**
     * Reactions whose author can still see the moment. After an unfriend the ex-friend's reactions
     * disappear from the owner's photo (and come back if they become friends again).
     */
    @Query(nativeQuery = true, value = """
            select r.* from reactions r
            join moments m on m.id = r.moment_id
            where r.moment_id in (:momentIds)
              and (r.user_id = m.sender_id or exists (
                  select 1 from friendships f where f.user_id = r.user_id and f.friend_id = m.sender_id))""")
    List<Reaction> findVisibleByMomentIds(Collection<Long> momentIds);

    /** Adds taps to this user's count for the emoji, up to {@link ReactionEmojis#MAX_TAPS}. */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into reactions (moment_id, user_id, emoji, taps, created_at, updated_at)
            values (:momentId, :userId, :emoji, :taps, :now, :now)
            on conflict (moment_id, user_id, emoji) do update
                set taps = least(reactions.taps + excluded.taps, 99), updated_at = excluded.updated_at""")
    int addTaps(long momentId, long userId, String emoji, int taps, Instant now);

    /** When this user last reacted to the moment with any emoji, or null. */
    @Query(nativeQuery = true, value = "select max(updated_at) from reactions where moment_id = :momentId and user_id = :userId")
    Instant lastReactedAt(long momentId, long userId);

    @Modifying
    @Query("delete from Reaction r where r.momentId = :momentId and r.userId = :userId")
    int deleteByMomentIdAndUserId(long momentId, long userId);
}
