package com.junseo.friend;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.safety.BlockRepository;
import com.junseo.user.InviteCodes;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserService;
import com.junseo.user.UserSummary;
import java.text.Collator;
import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {

    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final BlockRepository blocks;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public FriendService(
            FriendshipRepository friendships,
            UserRepository users,
            BlockRepository blocks,
            ApplicationEventPublisher events,
            Clock clock) {
        this.friendships = friendships;
        this.users = users;
        this.blocks = blocks;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> list(long me) {
        Collator korean = Collator.getInstance(Locale.KOREAN);
        return users.findAllById(friendships.findFriendIds(me)).stream()
                .sorted(Comparator.comparing(User::getDisplayName, korean).thenComparing(User::getId))
                .map(UserSummary::of)
                .toList();
    }

    @Transactional
    public UserSummary addByInviteCode(long me, String inviteCode) {
        User target = users.findByInviteCode(InviteCodes.normalize(inviteCode))
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_CODE_NOT_FOUND));
        long friendId = target.getId();
        if (friendId == me) {
            throw new ApiException(ErrorCode.CANNOT_ADD_SELF);
        }
        if (blocks.existsByBlockerIdAndBlockedId(me, friendId)) {
            throw new ApiException(ErrorCode.BLOCKED_USER);
        }
        // 나를 차단한 사람: 차단 사실을 알리지 않고, 없는 코드처럼 답한다
        if (blocks.existsByBlockerIdAndBlockedId(friendId, me)) {
            throw new ApiException(ErrorCode.INVITE_CODE_NOT_FOUND);
        }
        users.lockAll(List.of(me, friendId));
        if (friendships.existsByUserIdAndFriendId(me, friendId)) {
            throw new ApiException(ErrorCode.ALREADY_FRIENDS);
        }
        if (friendships.countByUserId(me) >= UserService.FRIEND_LIMIT) {
            throw new ApiException(ErrorCode.FRIEND_LIMIT_REACHED);
        }
        if (friendships.countByUserId(friendId) >= UserService.FRIEND_LIMIT) {
            throw new ApiException(ErrorCode.FRIEND_LIMIT_REACHED, "상대방의 친구가 이미 20명이라 추가할 수 없어요.");
        }
        var now = clock.instant();
        friendships.saveAll(List.of(new Friendship(me, friendId, now), new Friendship(friendId, me, now)));
        return UserSummary.of(target);
    }

    @Transactional
    public void unfriend(long me, long friendId) {
        if (friendships.deletePair(me, friendId) == 0) {
            throw ApiException.notFound();
        }
        events.publishEvent(new FriendEvents.Unfriended(me, friendId));
    }

    public boolean areFriends(long a, long b) {
        return friendships.existsByUserIdAndFriendId(a, b);
    }

    /** True when every one of these users is friends with every other one. */
    public boolean allFriends(Collection<Long> userIds) {
        long n = userIds.size();
        return n < 2 || friendships.countAmong(userIds) == n * (n - 1);
    }

    /** Which of my friends are friends with each other (for picking group chat members). */
    @Transactional(readOnly = true)
    public List<long[]> linksAmongFriends(long me) {
        List<Long> ids = friendships.findFriendIds(me);
        if (ids.size() < 2) {
            return List.of();
        }
        return friendships.findPairsAmong(ids).stream()
                .map(r -> new long[] {((Number) r[0]).longValue(), ((Number) r[1]).longValue()})
                .toList();
    }
}
