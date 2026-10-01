package com.junseo.push;

import com.junseo.chat.ChatEvents.MessageSent;
import com.junseo.comment.CommentEvents.CommentCreated;
import com.junseo.comment.CommentEvents.CommentDeleted;
import com.junseo.common.AppConfig;
import com.junseo.common.Texts;
import com.junseo.device.DeviceToken;
import com.junseo.device.DeviceTokenRepository;
import com.junseo.friend.FriendEvents.Unfriended;
import com.junseo.group.GroupEvents.GroupMessageSent;
import com.junseo.media.MediaStorage.Variant;
import com.junseo.media.MediaUrlSigner;
import com.junseo.moment.Moment;
import com.junseo.moment.MomentEvents.MomentCreated;
import com.junseo.moment.MomentEvents.MomentDeleted;
import com.junseo.moment.MomentRepository;
import com.junseo.push.PushSender.PushMessage;
import com.junseo.push.PushSender.PushOutcome;
import com.junseo.push.PushSender.PushType;
import com.junseo.reaction.ReactionEvents.ReactionRemoved;
import com.junseo.reaction.ReactionEvents.ReactionSet;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import java.text.Collator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns committed domain events into APNs pushes (see the push table in docs/api.md). Listeners run
 * after commit on the push executor, so a slow or failing APNs never affects the API response.
 */
@Component
public class PushNotifier {

    static final int MAX_BODY_CHARS = 80;
    static final int MAX_TITLE_CHARS = 40;
    static final String WIDGET_PAYLOAD = "{\"aps\":{\"content-changed\":true}}";

    private static final Logger log = LoggerFactory.getLogger(PushNotifier.class);

    private final PushSender sender;
    private final DeviceTokenRepository devices;
    private final UserRepository users;
    private final MomentRepository moments;
    private final MediaUrlSigner signer;
    private final ObjectMapper json;

    public PushNotifier(
            PushSender sender,
            DeviceTokenRepository devices,
            UserRepository users,
            MomentRepository moments,
            MediaUrlSigner signer,
            ObjectMapper json) {
        this.sender = sender;
        this.devices = devices;
        this.users = users;
        this.moments = moments;
        this.signer = signer;
        this.json = json;
    }

    @Async(AppConfig.PUSH_EXECUTOR)
    @TransactionalEventListener
    public void on(MomentCreated e) {
        guard("moment", () -> {
            String name = name(e.senderId());
            List<Long> recipients = moments.findCurrentRecipientIds(e.momentId());
            Map<String, Object> payload = alert(name, "새 사진을 보냈어요", "moment-" + e.momentId(), "moment");
            payload.put("momentId", e.momentId());
            payload.put("thumbUrl", signer.url(e.momentId(), Variant.THUMB));
            sendAlerts(recipients, payload);
            sendWidgetPushes(recipients);
        });
    }

    @Async(AppConfig.PUSH_EXECUTOR)
    @TransactionalEventListener
    public void on(ReactionSet e) {
        // A run of taps on one photo notifies the owner (and refreshes widgets) once; later taps in the
        // same minute still count, they just ride the next widget refresh.
        if (!e.firstInBurst()) {
            return;
        }
        guard("reaction", () -> moments.findById(e.momentId()).ifPresent(moment -> {
            String body = e.taps() > 1
                    ? name(e.userId()) + "님이 " + e.emoji() + " " + e.taps() + "개를 보냈어요"
                    : name(e.userId()) + "님이 " + e.emoji() + " 반응을 남겼어요";
            Map<String, Object> payload = alert(null, body, "moment-" + e.momentId(), "reaction");
            payload.put("momentId", e.momentId());
            sendAlerts(List.of(moment.getSenderId()), payload);
            sendWidgetPushes(widgetViewersExcept(moment, e.userId()));
        }));
    }

    @Async(AppConfig.PUSH_EXECUTOR)
    @TransactionalEventListener
    public void on(CommentCreated e) {
        guard("comment", () -> moments.findById(e.momentId()).ifPresent(moment -> {
            if (moment.getSenderId() != e.authorId()) {
                String body = name(e.authorId()) + ": " + Texts.truncate(e.text(), MAX_BODY_CHARS);
                Map<String, Object> payload = alert(null, body, "moment-" + e.momentId(), "comment");
                payload.put("momentId", e.momentId());
                sendAlerts(List.of(moment.getSenderId()), payload);
            }
            sendWidgetPushes(widgetViewersExcept(moment, e.authorId()));
        }));
    }

    @Async(AppConfig.PUSH_EXECUTOR)
    @TransactionalEventListener
    public void on(MessageSent e) {
        guard("message", () -> {
            String body = name(e.senderId()) + ": " + Texts.truncate(e.text(), MAX_BODY_CHARS);
            Map<String, Object> payload = alert(null, body, "message-" + e.senderId(), "message");
            payload.put("peerId", e.senderId());
            sendAlerts(List.of(e.receiverId()), payload);
        });
    }

    /** Everyone else in the group; without a group name the title is the other members' names, as in the app. */
    @Async(AppConfig.PUSH_EXECUTOR)
    @TransactionalEventListener
    public void on(GroupMessageSent e) {
        guard("group-message", () -> {
            Map<Long, User> people = users.mapById(e.memberIds());
            String body = name(people, e.senderId()) + ": " + Texts.truncate(e.text(), MAX_BODY_CHARS);
            for (long recipient : e.memberIds()) {
                if (recipient == e.senderId()) {
                    continue;
                }
                String title = e.groupName() != null
                        ? e.groupName()
                        : Texts.truncate(e.memberIds().stream()
                                .filter(id -> id != recipient)
                                .map(id -> name(people, id))
                                .sorted(Collator.getInstance(Locale.KOREAN))
                                .collect(Collectors.joining(", ")), MAX_TITLE_CHARS);
                Map<String, Object> payload = alert(title, body, "group-" + e.groupId(), "group-message");
                payload.put("groupId", e.groupId());
                sendAlerts(List.of(recipient), payload);
            }
        });
    }

    // Not in the push table: silent widget refreshes for changes that can alter what a widget shows.

    @Async(AppConfig.PUSH_EXECUTOR)
    @TransactionalEventListener
    public void on(ReactionRemoved e) {
        guard("reaction-removed", () -> moments.findById(e.momentId())
                .ifPresent(moment -> sendWidgetPushes(widgetViewersExcept(moment, e.userId()))));
    }

    @Async(AppConfig.PUSH_EXECUTOR)
    @TransactionalEventListener
    public void on(CommentDeleted e) {
        guard("comment-deleted", () -> moments.findById(e.momentId())
                .ifPresent(moment -> sendWidgetPushes(widgetViewersExcept(moment, e.deletedBy()))));
    }

    @Async(AppConfig.PUSH_EXECUTOR)
    @TransactionalEventListener
    public void on(MomentDeleted e) {
        guard("moment-deleted", () -> sendWidgetPushes(
                e.audience().stream().filter(id -> id != e.senderId()).toList()));
    }

    @Async(AppConfig.PUSH_EXECUTOR)
    @TransactionalEventListener
    public void on(Unfriended e) {
        guard("unfriended", () -> sendWidgetPushes(List.of(e.userId(), e.formerFriendId())));
    }

    /** Only people whose widget is showing this moment right now; the sender's widget never shows their own. */
    private List<Long> widgetViewersExcept(Moment moment, long actorId) {
        return moments.findWidgetViewerIds(moment.getId()).stream().filter(id -> id != actorId).toList();
    }

    private Map<String, Object> alert(String title, String body, String threadId, String type) {
        Map<String, Object> alert = new LinkedHashMap<>();
        if (title != null) {
            alert.put("title", title);
        }
        alert.put("body", body);
        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("alert", alert);
        aps.put("sound", "default");
        aps.put("mutable-content", 1);
        aps.put("thread-id", threadId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("aps", aps);
        payload.put("type", type);
        return payload;
    }

    private void sendAlerts(Collection<Long> userIds, Map<String, Object> payload) {
        dispatch(userIds, DeviceToken.KIND_APP, PushType.ALERT, json.writeValueAsString(payload));
    }

    private void sendWidgetPushes(Collection<Long> userIds) {
        dispatch(userIds, DeviceToken.KIND_WIDGET, PushType.WIDGETS, WIDGET_PAYLOAD);
    }

    private void dispatch(Collection<Long> userIds, String kind, PushType type, String payload) {
        if (userIds.isEmpty()) {
            return;
        }
        for (DeviceToken device : devices.findByUserIdInAndKind(userIds, kind)) {
            PushMessage message = new PushMessage(
                    device.getUserId(), device.getToken(), device.getEnvironment(), type, payload);
            PushOutcome outcome;
            try {
                outcome = sender.send(message);
            } catch (RuntimeException ex) {
                log.warn("Push to user {} failed", device.getUserId(), ex);
                continue;
            }
            if (outcome == PushOutcome.INVALID_TOKEN) {
                devices.deleteByToken(device.getToken());
            }
        }
    }

    private String name(long userId) {
        return users.findById(userId).map(User::getDisplayName).orElse("친구");
    }

    private static String name(Map<Long, User> people, long userId) {
        User user = people.get(userId);
        return user == null ? "친구" : user.getDisplayName();
    }

    private static void guard(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            log.warn("Could not send {} push", what, e);
        }
    }
}
