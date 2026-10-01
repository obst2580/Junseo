package com.junseo.reaction;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.moment.Moment;
import com.junseo.moment.MomentAccess;
import com.junseo.moment.MomentView;
import com.junseo.moment.MomentViews;
import com.junseo.reaction.ReactionEvents.ReactionRemoved;
import com.junseo.reaction.ReactionEvents.ReactionSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReactionService {

    /** Taps closer together than this count as one burst: the owner hears about the first one only. */
    static final Duration BURST = Duration.ofMinutes(1);

    private final ReactionRepository reactions;
    private final MomentAccess access;
    private final MomentViews views;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReactionService(
            ReactionRepository reactions,
            MomentAccess access,
            MomentViews views,
            ApplicationEventPublisher events,
            Clock clock) {
        this.reactions = reactions;
        this.access = access;
        this.views = views;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public MomentView react(long userId, long momentId, String emoji, int taps) {
        Moment moment = access.requireVisible(userId, momentId);
        if (moment.getSenderId() == userId) {
            throw new ApiException(ErrorCode.NOT_ALLOWED_ON_OWN_MOMENT, "내 사진에는 반응할 수 없어요.");
        }
        Instant now = clock.instant();
        Instant last = reactions.lastReactedAt(momentId, userId);
        boolean firstInBurst = last == null || last.isBefore(now.minus(BURST));
        reactions.addTaps(momentId, userId, emoji, taps, now);
        events.publishEvent(new ReactionSet(momentId, userId, emoji, taps, firstInBurst));
        return views.one(userId, moment);
    }

    @Transactional
    public void remove(long userId, long momentId) {
        access.requireVisible(userId, momentId);
        if (reactions.deleteByMomentIdAndUserId(momentId, userId) > 0) {
            events.publishEvent(new ReactionRemoved(momentId, userId));
        }
    }
}
