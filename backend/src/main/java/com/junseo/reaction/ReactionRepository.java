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

    /** Returns 1 when a reaction was inserted or changed, 0 when the same emoji was sent again. */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into reactions (moment_id, user_id, emoji, created_at, updated_at)
            values (:momentId, :userId, :emoji, :now, :now)
            on conflict (moment_id, user_id) do update
                set emoji = excluded.emoji, updated_at = excluded.updated_at
                where reactions.emoji <> excluded.emoji""")
    int upsert(long momentId, long userId, String emoji, Instant now);

    @Modifying
    @Query("delete from Reaction r where r.momentId = :momentId and r.userId = :userId")
    int deleteByMomentIdAndUserId(long momentId, long userId);
}
