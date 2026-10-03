package com.junseo.chat;

import com.junseo.chat.ChatEvents.MessageSent;
import com.junseo.chat.MessageView.MomentRef;
import com.junseo.common.ApiException;
import com.junseo.common.CursorPage;
import com.junseo.common.ErrorCode;
import com.junseo.friend.FriendService;
import com.junseo.media.MediaStorage.Variant;
import com.junseo.media.MediaUrlSigner;
import com.junseo.moment.Moment;
import com.junseo.moment.MomentAccess;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserService;
import com.junseo.user.UserSummary;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    public record Conversation(UserSummary peer, MessageView lastMessage, long unreadCount) {}

    private final MessageRepository messages;
    private final MomentAccess access;
    private final FriendService friends;
    private final UserService userService;
    private final UserRepository users;
    private final MediaUrlSigner signer;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ChatService(
            MessageRepository messages,
            MomentAccess access,
            FriendService friends,
            UserService userService,
            UserRepository users,
            MediaUrlSigner signer,
            ApplicationEventPublisher events,
            Clock clock) {
        this.messages = messages;
        this.access = access;
        this.friends = friends;
        this.userService = userService;
        this.users = users;
        this.signer = signer;
        this.events = events;
        this.clock = clock;
    }

    /** A private 1:1 reply to the photo's owner; seeing the photo already implies being friends. */
    @Transactional
    public MessageView reply(long userId, long momentId, String text, String clientId) {
        Optional<MessageView> earlier = sentEarlier(userId, clientId);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        Moment moment = access.requireVisible(userId, momentId);
        if (moment.getSenderId() == userId) {
            throw new ApiException(ErrorCode.NOT_ALLOWED_ON_OWN_MOMENT, "내 사진에는 답장할 수 없어요.");
        }
        return send(userId, moment.getSenderId(), momentId, text, clientId);
    }

    @Transactional
    public MessageView sendMessage(long userId, long peerId, String text, String clientId) {
        Optional<MessageView> earlier = sentEarlier(userId, clientId);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        userService.require(peerId);
        if (!friends.areFriends(userId, peerId)) {
            throw new ApiException(ErrorCode.NOT_FRIENDS);
        }
        return send(userId, peerId, null, text, clientId);
    }

    /** A retry of a send that already went through (same client id): the first copy, with no new push. */
    @Transactional(readOnly = true)
    public Optional<MessageView> sentEarlier(long userId, String clientId) {
        if (clientId == null) {
            return Optional.empty();
        }
        return messages.findBySenderIdAndClientId(userId, clientId).map(m -> views(userId, List.of(m)).getFirst());
    }

    /** History stays readable after an unfriend; only sending is blocked. */
    @Transactional(readOnly = true)
    public CursorPage<MessageView> thread(long userId, long peerId, String cursor, int limit) {
        userService.require(peerId);
        int size = CursorPage.clampLimit(limit);
        List<Message> rows = messages.findThread(
                Math.min(userId, peerId), Math.max(userId, peerId), CursorPage.before(cursor), size + 1);
        return CursorPage.of(rows, size, Message::getId, page -> views(userId, page));
    }

    @Transactional(readOnly = true)
    public List<Conversation> conversations(long userId) {
        List<Object[]> rows = messages.findConversations(userId);
        List<Long> peerIds = rows.stream().map(r -> ((Number) r[0]).longValue()).toList();
        List<Long> lastIds = rows.stream().map(r -> ((Number) r[1]).longValue()).toList();
        Map<Long, User> peers = users.mapById(peerIds);
        Map<Long, MessageView> lastById = views(userId, messages.findAllById(lastIds)).stream()
                .collect(Collectors.toMap(MessageView::id, v -> v));
        return rows.stream()
                .map(r -> new Conversation(
                        UserSummary.of(Objects.requireNonNull(peers.get(((Number) r[0]).longValue()))),
                        lastById.get(((Number) r[1]).longValue()),
                        ((Number) r[2]).longValue()))
                .toList();
    }

    @Transactional
    public void markRead(long userId, long peerId) {
        userService.require(peerId);
        if (messages.markRead(userId, peerId, clock.instant()) > 0) {
            events.publishEvent(new ChatEvents.MessagesRead(userId, peerId));
        }
    }

    private MessageView send(long senderId, long receiverId, Long momentId, String text, String clientId) {
        Message message = messages.save(new Message(senderId, receiverId, momentId, text, clock.instant(), clientId));
        events.publishEvent(new MessageSent(message.getId(), senderId, receiverId, text));
        return views(senderId, List.of(message)).getFirst();
    }

    private List<MessageView> views(long viewerId, List<Message> list) {
        Set<Long> visibleMoments = access.visibleIds(
                viewerId, list.stream().map(Message::getMomentId).filter(Objects::nonNull).distinct().toList());
        return list.stream()
                .map(m -> new MessageView(
                        m.getId(),
                        m.getSenderId(),
                        m.getReceiverId(),
                        m.getText(),
                        m.getCreatedAt(),
                        m.getReadAt(),
                        m.getMomentId() != null && visibleMoments.contains(m.getMomentId())
                                ? new MomentRef(m.getMomentId(), signer.url(m.getMomentId(), Variant.THUMB))
                                : null,
                        m.getSenderId() == viewerId ? m.getClientId() : null))
                .toList();
    }
}
