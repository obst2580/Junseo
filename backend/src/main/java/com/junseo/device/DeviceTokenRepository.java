package com.junseo.device;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    /** A token re-registered from another account moves to that account. */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into device_tokens (token, user_id, kind, environment, created_at, updated_at)
            values (:token, :userId, :kind, :environment, :now, :now)
            on conflict (token) do update
                set user_id = excluded.user_id, kind = excluded.kind,
                    environment = excluded.environment, updated_at = excluded.updated_at""")
    int upsert(String token, long userId, String kind, String environment, Instant now);

    List<DeviceToken> findByUserIdInAndKind(Collection<Long> userIds, String kind);

    @Transactional
    @Modifying
    @Query("delete from DeviceToken d where d.token = :token and d.userId = :userId")
    int deleteOwned(String token, long userId);

    @Transactional
    @Modifying
    @Query("delete from DeviceToken d where d.token = :token")
    int deleteByToken(String token);
}
