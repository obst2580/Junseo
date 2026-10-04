package com.junseo.widget;

import com.junseo.reaction.ReactionCount;
import com.junseo.user.UserSummary;
import java.time.Instant;
import java.util.List;

public record WidgetLatest(
        String version,
        WidgetMoment moment,
        List<ReactionCount> reactions,
        long reactionCount,
        List<WidgetComment> comments,
        long commentCount) {

    public record WidgetMoment(long id, UserSummary sender, Instant createdAt, String thumbUrl) {}

    public record WidgetComment(String author, String text) {}
}
