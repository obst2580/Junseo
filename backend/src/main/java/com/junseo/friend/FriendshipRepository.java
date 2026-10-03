package com.junseo.friend;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FriendshipRepository extends JpaRepository<Friendship, Friendship.Key> {

    boolean existsByUserIdAndFriendId(long userId, long friendId);

    long countByUserId(long userId);

    @Query("select f.friendId from Friendship f where f.userId = :userId")
    List<Long> findFriendIds(long userId);

    /** Rows in both directions among these users; n users are all friends with each other iff this is n·(n−1). */
    @Query(nativeQuery = true, value = "select count(*) from friendships where user_id in (:ids) and friend_id in (:ids)")
    long countAmong(Collection<Long> ids);

    /** Each friendship among these users once, as {@code [smallerId, largerId]}. */
    @Query(nativeQuery = true, value = """
            select user_id, friend_id from friendships
            where user_id in (:ids) and friend_id in (:ids) and user_id < friend_id""")
    List<Object[]> findPairsAmong(Collection<Long> ids);

    @Modifying
    @Query("delete from Friendship f where (f.userId = :a and f.friendId = :b) or (f.userId = :b and f.friendId = :a)")
    int deletePair(long a, long b);
}
