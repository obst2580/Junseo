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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReactionService {

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
    public MomentView react(long userId, long momentId, String emoji) {
        Moment moment = access.requireVisible(userId, momentId);
        if (moment.getSenderId() == userId) {
            throw new ApiException(ErrorCode.NOT_ALLOWED_ON_OWN_MOMENT, "내 사진에는 반응할 수 없어요.");
        }
        if (reactions.upsert(momentId, userId, emoji, clock.instant()) > 0) {
            events.publishEvent(new ReactionSet(momentId, userId, emoji));
        }
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
