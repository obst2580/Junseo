package com.junseo.account;

import com.junseo.common.security.Sessions;
import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.Transactions;
import com.junseo.friend.FriendEvents.Unfriended;
import com.junseo.friend.FriendshipRepository;
import com.junseo.group.ChatGroupRepository;
import com.junseo.media.MediaStorage;
import com.junseo.media.MediaStorage.Variant;
import com.junseo.moment.MomentRepository;
import com.junseo.realtime.RealtimeHub;
import com.junseo.safety.Bans;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting an account removes everything the person made: photos (files too), reactions, comments, messages,
 * group messages, friendships, devices. Friends' widgets are told to refresh so the photos disappear there as well.
 */
@Service
public class AccountService {

    /**
     * A LiliPlanet account has no password here: the app logs in again right before deleting, within this window.
     * The token's auth_time is that login (a renewed session token keeps its original sign-in time, so it never counts).
     */
    static final Duration REAUTH_WINDOW = Duration.ofMinutes(5);
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository users;
    private final FriendshipRepository friendships;
    private final MomentRepository moments;
    private final ChatGroupRepository groups;
    private final MediaStorage storage;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Sessions sessions;
    private final RealtimeHub hub;
    private final Bans bans;
    private final Clock clock;

    public AccountService(
            UserRepository users,
            FriendshipRepository friendships,
            MomentRepository moments,
            ChatGroupRepository groups,
            MediaStorage storage,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events,
            Sessions sessions,
            RealtimeHub hub,
            Bans bans,
            Clock clock) {
        this.users = users;
        this.friendships = friendships;
        this.moments = moments;
        this.groups = groups;
        this.storage = storage;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.sessions = sessions;
        this.hub = hub;
        this.bans = bans;
        this.clock = clock;
    }

    /**
     * The person deletes their own account, proving it is really them so a phone left unlocked can't do it:
     * a local account gives its password again, a LiliPlanet account sends a token from a login made just now.
     */
    @Transactional
    public void deleteOwn(long userId, String password, Jwt token) {
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (user.isPlatform()) {
            requireFreshLogin(token);
        } else if (password == null || user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.WRONG_PASSWORD);
        }
        delete(user);
    }

    /** Operator removes an account (a report was upheld). ban: also refuse the same person coming back. */
    @Transactional
    public void deleteByOperator(long userId, boolean ban, String reason) {
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        if (ban) bans.ban(user, reason == null || reason.isBlank() ? null : reason.strip(), clock.instant());
        delete(user);
    }

    /** auth_time: when the person signed in for the session this token belongs to (the sessions themselves go with the account). */
    private void requireFreshLogin(Jwt token) {
        Instant at = token.hasClaim(Sessions.AUTH_TIME_CLAIM) ? token.getClaimAsInstant(Sessions.AUTH_TIME_CLAIM) : null;
        Instant now = clock.instant();
        if (at == null || at.isBefore(now.minus(REAUTH_WINDOW)) || at.isAfter(now.plus(CLOCK_SKEW))) {
            throw new ApiException(ErrorCode.REAUTH_REQUIRED);
        }
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
            hub.closeUser(userId);
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
