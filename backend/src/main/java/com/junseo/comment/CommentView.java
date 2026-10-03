package com.junseo.comment;

import com.junseo.user.UserSummary;
import java.time.Instant;

public record CommentView(long id, long momentId, UserSummary author, String text, Instant createdAt) {

    public static CommentView of(Comment c, UserSummary author) {
        return new CommentView(c.getId(), c.getMomentId(), author, c.getText(), c.getCreatedAt());
    }
}
