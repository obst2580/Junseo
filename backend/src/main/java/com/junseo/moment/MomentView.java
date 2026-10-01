package com.junseo.moment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.junseo.comment.CommentView;
import com.junseo.reaction.ReactionCount;
import com.junseo.user.UserSummary;
import java.time.Instant;
import java.util.List;

/** The contract's Moment; {@code comments} (all, oldest first) is present only on the detail endpoint. */
public record MomentView(
        long id,
        UserSummary sender,
        Instant createdAt,
        String imageUrl,
        String thumbUrl,
        List<ReactionCount> reactions,
        String myReaction,
        long commentCount,
        List<CommentView> recentComments,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<CommentView> comments) {}
