package com.junseo.group;

import com.junseo.chat.ChatController.MessageRequest;
import com.junseo.common.CursorPage;
import com.junseo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    public record CreateGroupRequest(
            @NotNull(message = "함께할 친구를 골라 주세요.") List<Long> memberIds,
            @Size(max = 30, message = "방 이름은 30자까지 쓸 수 있어요.") String name) {}

    public record GroupsResponse(List<GroupView> items) {}

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GroupView create(@CurrentUser long me, @Valid @RequestBody CreateGroupRequest request) {
        return groupService.create(me, request.memberIds(), request.name());
    }

    @GetMapping
    GroupsResponse list(@CurrentUser long me) {
        return new GroupsResponse(groupService.list(me));
    }

    @GetMapping("/{id}")
    GroupView get(@CurrentUser long me, @PathVariable long id) {
        return groupService.get(me, id);
    }

    @GetMapping("/{id}/messages")
    CursorPage<GroupMessageView> messages(
            @CurrentUser long me,
            @PathVariable long id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return groupService.thread(me, id, cursor, limit);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    GroupMessageView send(@CurrentUser long me, @PathVariable long id, @Valid @RequestBody MessageRequest request) {
        try {
            return groupService.send(me, id, request.text(), request.clientId());
        } catch (DataIntegrityViolationException e) {
            // The same send raced itself (a retry while the first was still in flight): hand back the first copy.
            return groupService.sentEarlier(me, request.clientId()).orElseThrow(() -> e);
        }
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@CurrentUser long me, @PathVariable long id) {
        groupService.markRead(me, id);
    }

    /** Leave the group; the last member out deletes it. */
    @DeleteMapping("/{id}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@CurrentUser long me, @PathVariable long id) {
        groupService.leave(me, id);
    }
}
