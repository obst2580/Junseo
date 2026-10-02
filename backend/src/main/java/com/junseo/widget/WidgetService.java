package com.junseo.widget;

import com.junseo.comment.Comment;
import com.junseo.comment.CommentRepository;
import com.junseo.media.MediaStorage.Variant;
import com.junseo.media.MediaUrlSigner;
import com.junseo.moment.Moment;
import com.junseo.moment.MomentRepository;
import com.junseo.reaction.Reaction;
import com.junseo.reaction.ReactionCount;
import com.junseo.reaction.ReactionRepository;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserSummary;
import com.junseo.widget.WidgetLatest.WidgetComment;
import com.junseo.widget.WidgetLatest.WidgetMoment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WidgetService {

    static final int TOP_REACTIONS = 3;
    static final int RECENT_COMMENTS = 2;
    /** The widget pages through at most this many photos ... */
    public static final int FEED_SIZE = 5;
    /** ... taken within this window (or just the newest one when none is that recent). */
    public static final Duration FEED_WINDOW = Duration.ofHours(24);

    private final MomentRepository moments;
    private final ReactionRepository reactions;
    private final CommentRepository comments;
    private final UserRepository users;
    private final MediaUrlSigner signer;
    private final Clock clock;

    public WidgetService(
            MomentRepository moments,
            ReactionRepository reactions,
            CommentRepository comments,
            UserRepository users,
            MediaUrlSigner signer,
            Clock clock) {
        this.moments = moments;
        this.reactions = reactions;
        this.comments = comments;
        this.users = users;
        this.signer = signer;
        this.clock = clock;
    }

    /** The newest friend photo I received that I can still see; my own photos never show here. */
    @Transactional(readOnly = true)
    public Optional<WidgetLatest> latest(long viewerId) {
        return moments.findLatestReceived(viewerId).map(this::build);
    }

    /**
     * What the widget pages through: the newest {@link #FEED_SIZE} friend photos taken in the last
     * {@link #FEED_WINDOW}, newest first, or just the newest one when none is that recent.
     * {@code fromId} narrows it to one friend (a widget set to that person); null means every friend.
     */
    @Transactional(readOnly = true)
    public Optional<WidgetFeed> feed(long viewerId, Long fromId) {
        long sender = fromId == null ? 0 : fromId;
        List<Moment> recent = moments.findRecentReceived(viewerId, sender, clock.instant().minus(FEED_WINDOW), FEED_SIZE);
        if (recent.isEmpty()) {
            recent = moments.findLatestReceivedFrom(viewerId, sender).map(List::of).orElse(List.of());
        }
        if (recent.isEmpty()) {
            return Optional.empty();
        }
        List<WidgetLatest> items = recent.stream().map(this::build).toList();
        String version = items.getFirst().moment().id() + "-" + digest(String.join(",", items.stream().map(WidgetLatest::version).toList()));
        return Optional.of(new WidgetFeed(version, items));
    }

    private WidgetLatest build(Moment moment) {
        List<Reaction> all = reactions.findVisibleByMomentIds(List.of(moment.getId()));
        List<Comment> allComments = comments.findVisibleByMomentId(moment.getId());
        List<Comment> recent = allComments.subList(Math.max(0, allComments.size() - RECENT_COMMENTS), allComments.size());

        Set<Long> userIds = new HashSet<>();
        userIds.add(moment.getSenderId());
        recent.forEach(c -> userIds.add(c.getAuthorId()));
        Map<Long, User> usersById = users.mapById(userIds);

        List<ReactionCount> counts = ReactionCount.summarize(all);
        WidgetMoment widgetMoment = new WidgetMoment(
                moment.getId(),
                UserSummary.of(usersById.get(moment.getSenderId())),
                moment.getCreatedAt(),
                signer.url(moment.getId(), Variant.THUMB));
        List<WidgetComment> shown = recent.stream()
                .map(c -> new WidgetComment(usersById.get(c.getAuthorId()).getDisplayName(), c.getText()))
                .toList();
        String version = moment.getId() + "-" + fingerprint(widgetMoment, all, allComments, shown);
        return new WidgetLatest(
                version,
                widgetMoment,
                counts.subList(0, Math.min(TOP_REACTIONS, counts.size())),
                counts.stream().mapToLong(ReactionCount::count).sum(),
                shown,
                allComments.size());
    }

    /**
     * Hash of everything the widget renders plus the full reaction/comment state, so any reaction or
     * comment change (even outside the top 3 / last 2) yields a new version. The signed thumb URL is
     * included too: it rotates daily, and a 304 must never pin a client to an expiring URL.
     */
    private static String fingerprint(
            WidgetMoment moment, List<Reaction> reactions, List<Comment> comments, List<WidgetComment> shown) {
        StringBuilder s = new StringBuilder()
                .append(moment.id()).append('|').append(moment.sender().id()).append('|')
                .append(moment.sender().displayName()).append('|').append(moment.createdAt()).append('|')
                .append(moment.thumbUrl()).append("|r");
        reactions.stream()
                .sorted(Comparator.comparing(Reaction::getUserId).thenComparing(Reaction::getEmoji))
                .forEach(r -> s.append(':').append(r.getUserId()).append('=').append(r.getEmoji()).append('x').append(r.getTaps()));
        s.append("|c");
        comments.forEach(c -> s.append(':').append(c.getId()));
        s.append("|s");
        shown.forEach(c -> s.append(':').append(c.author()));
        return digest(s.toString());
    }

    private static String digest(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
