package com.junseo.comment;

import com.junseo.comment.CommentEvents.CommentCreated;
import com.junseo.comment.CommentEvents.CommentDeleted;
import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.moment.Moment;
import com.junseo.moment.MomentAccess;
import com.junseo.user.UserService;
import com.junseo.user.UserSummary;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository comments;
    private final MomentAccess access;
    private final UserService users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CommentService(
            CommentRepository comments,
            MomentAccess access,
            UserService users,
            ApplicationEventPublisher events,
            Clock clock) {
        this.comments = comments;
        this.access = access;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CommentView create(long authorId, long momentId, String text) {
        access.requireVisible(authorId, momentId);
        Comment comment = comments.save(new Comment(momentId, authorId, text, clock.instant()));
        events.publishEvent(new CommentCreated(comment.getId(), momentId, authorId, text));
        return CommentView.of(comment, UserSummary.of(users.require(authorId)));
    }

    /** The author or the moment's owner may delete. */
    @Transactional
    public void delete(long userId, long commentId) {
        Comment comment = comments.findById(commentId).orElseThrow(ApiException::notFound);
        Moment moment = access.requireVisible(userId, comment.getMomentId());
        if (comment.getAuthorId() != userId && moment.getSenderId() != userId) {
            throw new ApiException(ErrorCode.FORBIDDEN, "내 댓글이나 내 사진의 댓글만 지울 수 있어요.");
        }
        comments.delete(comment);
        events.publishEvent(new CommentDeleted(moment.getId(), userId));
    }

    /** Operator removes a reported comment. */
    @Transactional
    public void removeByOperator(long commentId) {
        Comment comment = comments.findById(commentId).orElseThrow(ApiException::notFound);
        comments.delete(comment);
        events.publishEvent(new CommentDeleted(comment.getMomentId(), comment.getAuthorId()));
    }
}
