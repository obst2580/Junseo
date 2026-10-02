package com.junseo.chat;

import com.junseo.common.CursorPage;
import com.junseo.common.security.CurrentUser;
import com.junseo.group.GroupService;
import com.junseo.group.GroupService.GroupConversation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    /** clientId: optional id the app picks per send; sending it again returns the first copy. */
    public record MessageRequest(
            @NotBlank(message = "메시지를 입력해 주세요.") @Size(max = 500, message = "메시지는 1~500자로 입력해 주세요.")
            String text,
            @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "잘못된 메시지 ID예요.")
            String clientId) {

        public MessageRequest {
            text = text == null ? null : text.strip();
        }
    }

    /** items: 1:1 chats; groups: group chats. Each list is newest first; the app merges them. */
    public record ConversationsResponse(List<ChatService.Conversation> items, List<GroupConversation> groups) {}

    private final ChatService chatService;
    private final GroupService groupService;

    public ChatController(ChatService chatService, GroupService groupService) {
        this.chatService = chatService;
        this.groupService = groupService;
    }

    @PostMapping("/api/moments/{id}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    MessageView reply(@CurrentUser long me, @PathVariable long id, @Valid @RequestBody MessageRequest request) {
        try {
            return chatService.reply(me, id, request.text(), request.clientId());
        } catch (DataIntegrityViolationException e) {
            return chatService.sentEarlier(me, request.clientId()).orElseThrow(() -> e);
        }
    }

    @GetMapping("/api/conversations")
    ConversationsResponse conversations(@CurrentUser long me) {
        return new ConversationsResponse(chatService.conversations(me), groupService.conversations(me));
    }

    @GetMapping("/api/conversations/{peerId}/messages")
    CursorPage<MessageView> messages(
            @CurrentUser long me,
            @PathVariable long peerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return chatService.thread(me, peerId, cursor, limit);
    }

    @PostMapping("/api/conversations/{peerId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageView send(@CurrentUser long me, @PathVariable long peerId, @Valid @RequestBody MessageRequest request) {
        try {
            return chatService.sendMessage(me, peerId, request.text(), request.clientId());
        } catch (DataIntegrityViolationException e) {
            // The same send raced itself (a retry while the first was still in flight): hand back the first copy.
            return chatService.sentEarlier(me, request.clientId()).orElseThrow(() -> e);
        }
    }

    @PostMapping("/api/conversations/{peerId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@CurrentUser long me, @PathVariable long peerId) {
        chatService.markRead(me, peerId);
    }
}
