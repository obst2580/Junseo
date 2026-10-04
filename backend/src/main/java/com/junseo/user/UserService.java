package com.junseo.user;

import com.junseo.common.ApiException;
import com.junseo.friend.FriendshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    public static final int FRIEND_LIMIT = 20;

    private final UserRepository users;
    private final FriendshipRepository friendships;

    public UserService(UserRepository users, FriendshipRepository friendships) {
        this.users = users;
        this.friendships = friendships;
    }

    @Transactional(readOnly = true)
    public Me me(long userId) {
        return toMe(require(userId));
    }

    @Transactional
    public Me rename(long userId, String displayName) {
        User user = require(userId);
        user.setDisplayName(displayName);
        return toMe(user);
    }

    @Transactional
    public Me rotateInviteCode(long userId) {
        User user = require(userId);
        user.setInviteCode(newInviteCode());
        users.flush();
        return toMe(user);
    }

    public String newInviteCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = InviteCodes.random();
            if (!users.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not allocate a unique invite code");
    }

    public Me toMe(User user) {
        return new Me(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getInviteCode(),
                friendships.countByUserId(user.getId()),
                FRIEND_LIMIT,
                !user.isOnboarded(),
                user.isPlatform() ? "platform" : "password");
    }

    public User require(long userId) {
        return users.findById(userId).orElseThrow(ApiException::notFound);
    }
}
