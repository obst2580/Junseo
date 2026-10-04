package com.junseo.safety;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.friend.FriendEvents.Unfriended;
import com.junseo.friend.FriendshipRepository;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserSummary;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차단: 친구가 끊기고(사진은 친구끼리만 보여서 서로의 사진이 사라진다), 다시 친구가 될 수 없고,
 * 차단한 사람에게는 상대의 댓글 · 1:1 대화 · 단챗 메시지가 보이지 않고 알림도 오지 않는다. 상대에게는 알리지 않는다.
 */
@Service
public class BlockService {

    private final BlockRepository blocks;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BlockService(
            BlockRepository blocks, FriendshipRepository friendships, UserRepository users, ApplicationEventPublisher events, Clock clock) {
        this.blocks = blocks;
        this.friendships = friendships;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> list(long me) {
        List<Block> mine = blocks.findByBlockerIdOrderByCreatedAtDesc(me);
        Map<Long, User> people = users.mapById(mine.stream().map(Block::getBlockedId).toList());
        return mine.stream().map(b -> people.get(b.getBlockedId())).filter(u -> u != null).map(UserSummary::of).toList();
    }

    @Transactional
    public void block(long me, long userId) {
        if (userId == me) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "나를 차단할 수는 없어요.");
        }
        if (!users.existsById(userId)) {
            throw ApiException.notFound();
        }
        if (!blocks.existsByBlockerIdAndBlockedId(me, userId)) {
            blocks.save(new Block(me, userId, clock.instant()));
        }
        if (friendships.deletePair(me, userId) > 0) {
            events.publishEvent(new Unfriended(me, userId));
        }
    }

    @Transactional
    public void unblock(long me, long userId) {
        if (blocks.unblock(me, userId) == 0) {
            throw ApiException.notFound();
        }
    }

    public boolean eitherBlocked(long a, long b) {
        return blocks.existsByBlockerIdAndBlockedId(a, b) || blocks.existsByBlockerIdAndBlockedId(b, a);
    }
}
