package com.junseo.group;

import com.junseo.common.ApiException;
import com.junseo.common.CursorPage;
import com.junseo.common.ErrorCode;
import com.junseo.friend.FriendService;
import com.junseo.group.GroupEvents.GroupMessageSent;
import com.junseo.user.User;
import com.junseo.user.UserRepository;
import com.junseo.user.UserSummary;
import java.text.Collator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

    /** Including the creator. */
    public static final int MAX_MEMBERS = 20;
    /** Fewer than two others would just be a 1:1 chat. */
    public static final int MIN_OTHERS = 2;

    public record GroupConversation(GroupView group, GroupMessageView lastMessage, long unreadCount) {}

    private final ChatGroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupMessageRepository messages;
    private final FriendService friends;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public GroupService(
            ChatGroupRepository groups,
            GroupMemberRepository members,
            GroupMessageRepository messages,
            FriendService friends,
            UserRepository users,
            ApplicationEventPublisher events,
            Clock clock) {
        this.groups = groups;
        this.members = members;
        this.messages = messages;
        this.friends = friends;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /** Everyone has to be my friend, and friends with each other too. */
    @Transactional
    public GroupView create(long me, List<Long> memberIds, String name) {
        Set<Long> others = memberIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id != me)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (others.size() < MIN_OTHERS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "나 말고 2명 이상 골라 주세요.");
        }
        if (others.size() + 1 > MAX_MEMBERS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "단챗은 " + MAX_MEMBERS + "명까지 만들 수 있어요.");
        }
        if (others.stream().anyMatch(id -> !friends.areFriends(me, id))) {
            throw new ApiException(ErrorCode.NOT_FRIENDS, "친구만 단챗에 초대할 수 있어요.");
        }
        List<Long> everyone = new ArrayList<>(others);
        everyone.add(me);
        if (!friends.allFriends(everyone)) {
            throw new ApiException(ErrorCode.NOT_MUTUAL_FRIENDS);
        }
        Instant now = clock.instant();
        ChatGroup group = groups.save(new ChatGroup(name == null || name.isBlank() ? null : name.strip(), me, now));
        List<GroupMember> joined = members.saveAll(everyone.stream().map(id -> new GroupMember(group.getId(), id, now)).toList());
        return view(group, joined, users.mapById(everyone));
    }

    /** My groups, newest first. */
    @Transactional(readOnly = true)
    public List<GroupView> list(long me) {
        List<Long> ids = members.findGroupIds(me);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, List<GroupMember>> byGroup = members.findByGroupIdIn(ids).stream()
                .collect(Collectors.groupingBy(GroupMember::getGroupId));
        Map<Long, User> people = users.mapById(byGroup.values().stream()
                .flatMap(List::stream).map(GroupMember::getUserId).distinct().toList());
        return groups.findAllById(ids).stream()
                .sorted(Comparator.comparing(ChatGroup::getCreatedAt).reversed().thenComparing(ChatGroup::getId, Comparator.reverseOrder()))
                .map(g -> view(g, byGroup.getOrDefault(g.getId(), List.of()), people))
                .toList();
    }

    /** My groups with their newest message and my unread count, most recent activity first. */
    @Transactional(readOnly = true)
    public List<GroupConversation> conversations(long me) {
        List<GroupView> mine = list(me);
        if (mine.isEmpty()) {
            return List.of();
        }
        List<Long> ids = mine.stream().map(GroupView::id).toList();
        Map<Long, List<GroupMember>> byGroup = members.findByGroupIdIn(ids).stream()
                .collect(Collectors.groupingBy(GroupMember::getGroupId));
        Map<Long, GroupMessage> latest = messages.findLatest(ids).stream()
                .collect(Collectors.toMap(GroupMessage::getGroupId, Function.identity()));
        Map<Long, Long> unread = members.findUnreadCounts(me).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));
        return mine.stream()
                .map(g -> {
                    GroupMessage last = latest.get(g.id());
                    return new GroupConversation(
                            g,
                            last == null ? null : messageView(last, byGroup.getOrDefault(g.id(), List.of())),
                            unread.getOrDefault(g.id(), 0L));
                })
                .sorted(Comparator.comparing(GroupService::lastActivity).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupView get(long me, long groupId) {
        ChatGroup group = requireMember(me, groupId);
        List<GroupMember> joined = members.findByGroupId(groupId);
        return view(group, joined, users.mapById(joined.stream().map(GroupMember::getUserId).toList()));
    }

    @Transactional(readOnly = true)
    public CursorPage<GroupMessageView> thread(long me, long groupId, String cursor, int limit) {
        requireMember(me, groupId);
        int size = CursorPage.clampLimit(limit);
        List<GroupMessage> rows = messages.findPage(groupId, CursorPage.before(cursor), size + 1);
        List<GroupMember> joined = members.findByGroupId(groupId);
        return CursorPage.of(rows, size, GroupMessage::getId, page -> page.stream().map(m -> messageView(m, joined)).toList());
    }

    @Transactional
    public GroupMessageView send(long me, long groupId, String text) {
        ChatGroup group = requireMember(me, groupId);
        List<Long> memberIds = members.findByGroupId(groupId).stream().map(GroupMember::getUserId).toList();
        GroupMessage message = messages.save(new GroupMessage(groupId, me, text, clock.instant()));
        // Sending means I have seen everything up to my own message.
        members.advanceRead(groupId, me, message.getId());
        events.publishEvent(new GroupMessageSent(message.getId(), groupId, group.getName(), me, memberIds, text));
        return new GroupMessageView(
                message.getId(), groupId, me, message.getText(), message.getCreatedAt(), memberIds.size() - 1);
    }

    @Transactional
    public void markRead(long me, long groupId) {
        requireMember(me, groupId);
        members.advanceRead(groupId, me, messages.findLatestId(groupId).orElse(0L));
    }

    /** The last one out deletes the group and its messages. */
    @Transactional
    public void leave(long me, long groupId) {
        requireMember(me, groupId);
        members.leave(groupId, me);
        if (members.countByGroupId(groupId) == 0) {
            groups.deleteById(groupId);
        }
    }

    /** Not a member reads the same as "no such group". */
    private ChatGroup requireMember(long me, long groupId) {
        if (!members.existsByGroupIdAndUserId(groupId, me)) {
            throw ApiException.notFound();
        }
        return groups.findById(groupId).orElseThrow(ApiException::notFound);
    }

    private static GroupMessageView messageView(GroupMessage m, List<GroupMember> joined) {
        int unread = (int) joined.stream()
                .filter(member -> !Objects.equals(member.getUserId(), m.getSenderId()) && member.getLastReadId() < m.getId())
                .count();
        return new GroupMessageView(m.getId(), m.getGroupId(), m.getSenderId(), m.getText(), m.getCreatedAt(), unread);
    }

    private static GroupView view(ChatGroup group, List<GroupMember> joined, Map<Long, User> people) {
        Collator korean = Collator.getInstance(Locale.KOREAN);
        List<UserSummary> summaries = joined.stream()
                .map(member -> people.get(member.getUserId()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(User::getDisplayName, korean).thenComparing(User::getId))
                .map(UserSummary::of)
                .toList();
        return new GroupView(group.getId(), group.getName(), summaries, group.getCreatedAt());
    }

    private static Instant lastActivity(GroupConversation c) {
        return c.lastMessage() != null ? c.lastMessage().createdAt() : c.group().createdAt();
    }
}
