package com.junseo.moment;

import com.junseo.comment.Comment;
import com.junseo.comment.CommentRepository;
import com.junseo.comment.CommentView;
import com.junseo.media.MediaStorage.Variant;
import com.junseo.media.MediaUrlSigner;
import com.junseo.reaction.Reaction;
import com.junseo.reaction.ReactionCount;
import com.junseo.reaction.ReactionRepository;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserSummary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds contract Moments for a page with a fixed number of queries, whatever the page size. */
@Component
public class MomentViews {

    static final int RECENT_COMMENTS = 2;

    private final ReactionRepository reactions;
    private final CommentRepository comments;
    private final UserRepository users;
    private final MediaUrlSigner signer;

    public MomentViews(
            ReactionRepository reactions, CommentRepository comments, UserRepository users, MediaUrlSigner signer) {
        this.reactions = reactions;
        this.comments = comments;
        this.users = users;
        this.signer = signer;
    }

    public List<MomentView> list(long viewerId, List<Moment> moments) {
        if (moments.isEmpty()) {
            return List.of();
        }
        List<Long> ids = moments.stream().map(Moment::getId).toList();
        Map<Long, List<Reaction>> reactionsByMoment = reactions.findVisibleByMomentIds(ids).stream()
                .collect(Collectors.groupingBy(Reaction::getMomentId));
        Map<Long, List<Comment>> recentByMoment = comments.findRecentVisible(ids, RECENT_COMMENTS, viewerId).stream()
                .collect(Collectors.groupingBy(Comment::getMomentId));
        Map<Long, Long> commentCounts = new HashMap<>();
        for (Object[] row : comments.countVisibleByMomentIds(ids, viewerId)) {
            commentCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        Set<Long> userIds = new HashSet<>();
        moments.forEach(m -> userIds.add(m.getSenderId()));
        recentByMoment.values().forEach(cs -> cs.forEach(c -> userIds.add(c.getAuthorId())));
        Map<Long, User> usersById = users.mapById(userIds);

        return moments.stream().map(m -> build(
                        viewerId,
                        m,
                        reactionsByMoment.getOrDefault(m.getId(), List.of()),
                        recentByMoment.getOrDefault(m.getId(), List.of()),
                        commentCounts.getOrDefault(m.getId(), 0L),
                        null,
                        usersById))
                .toList();
    }

    public MomentView one(long viewerId, Moment moment) {
        return list(viewerId, List.of(moment)).getFirst();
    }

    public MomentView detail(long viewerId, Moment moment) {
        List<Comment> all = comments.findVisibleByMomentId(moment.getId(), viewerId);
        Set<Long> userIds = new HashSet<>();
        userIds.add(moment.getSenderId());
        all.forEach(c -> userIds.add(c.getAuthorId()));
        Map<Long, User> usersById = users.mapById(userIds);
        List<Comment> recent = all.subList(Math.max(0, all.size() - RECENT_COMMENTS), all.size());
        return build(
                viewerId,
                moment,
                reactions.findVisibleByMomentIds(List.of(moment.getId())),
                recent,
                all.size(),
                all,
                usersById);
    }

    private MomentView build(
            long viewerId,
            Moment m,
            List<Reaction> momentReactions,
            List<Comment> recent,
            long commentCount,
            List<Comment> allComments,
            Map<Long, User> usersById) {
        List<ReactionCount> mine = ReactionCount.summarize(
                momentReactions.stream().filter(r -> r.getUserId() == viewerId).toList());
        return new MomentView(
                m.getId(),
                UserSummary.of(usersById.get(m.getSenderId())),
                m.getCreatedAt(),
                signer.url(m.getId(), Variant.FULL),
                signer.url(m.getId(), Variant.THUMB),
                ReactionCount.summarize(momentReactions),
                mine,
                commentCount,
                toViews(recent, usersById),
                allComments == null ? null : toViews(allComments, usersById));
    }

    private static List<CommentView> toViews(List<Comment> list, Map<Long, User> usersById) {
        return list.stream()
                .map(c -> CommentView.of(c, UserSummary.of(Objects.requireNonNull(usersById.get(c.getAuthorId())))))
                .toList();
    }
}
