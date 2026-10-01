package com.junseo.user;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    /** Row locks in id order serialize concurrent friend adds that touch the same users. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id in :ids order by u.id")
    List<User> lockAll(Collection<Long> ids);

    default Map<Long, User> mapById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
