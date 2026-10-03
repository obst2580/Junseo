package com.junseo.account;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.Transactions;
import com.junseo.friend.FriendEvents.Unfriended;
import com.junseo.friend.FriendshipRepository;
import com.junseo.group.ChatGroupRepository;
import com.junseo.media.MediaStorage;
import com.junseo.media.MediaStorage.Variant;
import com.junseo.moment.MomentRepository;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting an account removes everything the person made: photos (files too), reactions, comments, messages,
 * group messages, friendships, devices. Friends' widgets are told to refresh so the photos disappear there as well.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository users;
    private final FriendshipRepository friendships;
    private final MomentRepository moments;
    private final ChatGroupRepository groups;
    private final MediaStorage storage;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    public AccountService(
            UserRepository users,
            FriendshipRepository friendships,
            MomentRepository moments,
            ChatGroupRepository groups,
            MediaStorage storage,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events) {
        this.users = users;
        this.friendships = friendships;
        this.moments = moments;
        this.groups = groups;
        this.storage = storage;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    /** The person deletes their own account; the password is asked again so a phone left unlocked can't do it. */
    @Transactional
    public void deleteOwn(long userId, String password) {
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.WRONG_PASSWORD);
        }
        delete(user);
    }

    /** Operator removes an account (a report was upheld). */
    @Transactional
    public void deleteByOperator(long userId) {
        delete(users.findById(userId).orElseThrow(ApiException::notFound));
    }

    private void delete(User user) {
        long userId = user.getId();
        List<Long> friendIds = friendships.findFriendIds(userId);
        List<Long> momentIds = moments.findIdsBySenderId(userId);
        // Rows that point at the user go with it (on delete cascade); reports keep a null reporter / target.
        users.delete(user);
        users.flush();
        groups.deleteEmpty();
        Transactions.afterCommit(() -> {
            for (long id : momentIds) {
                storage.delete(MediaStorage.key(id, Variant.FULL));
                storage.delete(MediaStorage.key(id, Variant.THUMB));
            }
        });
        for (long friendId : friendIds) {
            events.publishEvent(new Unfriended(userId, friendId));
        }
        log.info("Account deleted: user={} photos={} friends={}", userId, momentIds.size(), friendIds.size());
    }
}
