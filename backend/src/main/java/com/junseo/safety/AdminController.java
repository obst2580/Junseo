package com.junseo.safety;

import com.junseo.account.AccountService;
import com.junseo.chat.MessageRepository;
import com.junseo.comment.CommentService;
import com.junseo.common.ApiException;
import com.junseo.common.security.AdminAuth;
import com.junseo.group.GroupMessageRepository;
import com.junseo.moment.MomentService;
import com.junseo.safety.ReportService.ReportView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자가 신고를 보고 처리한다 (App Store: 신고는 24시간 안에 확인해 내용을 지우거나 사용자를 내보낸다).
 * 모두 X-Admin-Token 으로 확인한다 ({@link AdminAuth}).
 */
@RestController
public class AdminController {

    public record Resolve(String resolution) {}

    public record ReportList(List<ReportView> items) {}

    private final AdminAuth admin;
    private final ReportService reports;
    private final MomentService moments;
    private final CommentService comments;
    private final MessageRepository messages;
    private final GroupMessageRepository groupMessages;
    private final AccountService accounts;

    public AdminController(
            AdminAuth admin,
            ReportService reports,
            MomentService moments,
            CommentService comments,
            MessageRepository messages,
            GroupMessageRepository groupMessages,
            AccountService accounts) {
        this.admin = admin;
        this.reports = reports;
        this.moments = moments;
        this.comments = comments;
        this.messages = messages;
        this.groupMessages = groupMessages;
        this.accounts = accounts;
    }

    /** 처리하지 않은 신고 (오래된 것부터). all=true 면 처리한 것까지 최근 순으로. */
    @GetMapping("/api/admin/reports")
    ReportList reports(@RequestHeader(value = AdminAuth.HEADER, required = false) String token, @RequestParam(defaultValue = "false") boolean all) {
        admin.check(token);
        return new ReportList(reports.list(all));
    }

    @PostMapping("/api/admin/reports/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resolve(@RequestHeader(value = AdminAuth.HEADER, required = false) String token, @PathVariable long id, @RequestBody(required = false) Resolve body) {
        admin.check(token);
        reports.resolve(id, body == null ? null : body.resolution());
    }

    @DeleteMapping("/api/admin/moments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMoment(@RequestHeader(value = AdminAuth.HEADER, required = false) String token, @PathVariable long id) {
        admin.check(token);
        moments.removeByOperator(id);
    }

    @DeleteMapping("/api/admin/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeComment(@RequestHeader(value = AdminAuth.HEADER, required = false) String token, @PathVariable long id) {
        admin.check(token);
        comments.removeByOperator(id);
    }

    @DeleteMapping("/api/admin/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removeMessage(@RequestHeader(value = AdminAuth.HEADER, required = false) String token, @PathVariable long id) {
        admin.check(token);
        messages.delete(messages.findById(id).orElseThrow(ApiException::notFound));
    }

    @DeleteMapping("/api/admin/group-messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removeGroupMessage(@RequestHeader(value = AdminAuth.HEADER, required = false) String token, @PathVariable long id) {
        admin.check(token);
        groupMessages.delete(groupMessages.findById(id).orElseThrow(ApiException::notFound));
    }

    /** 계정을 지운다 (사진 · 댓글 · 메시지까지 모두). */
    @DeleteMapping("/api/admin/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeUser(@RequestHeader(value = AdminAuth.HEADER, required = false) String token, @PathVariable long id) {
        admin.check(token);
        accounts.deleteByOperator(id);
    }
}
