package com.junseo.comment;

import com.junseo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    public record CommentRequest(
            @NotBlank(message = "댓글을 입력해 주세요.") @Size(max = 100, message = "댓글은 1~100자로 입력해 주세요.")
            String text) {

        public CommentRequest {
            text = text == null ? null : text.strip();
        }
    }

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/api/moments/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentView create(@CurrentUser long me, @PathVariable long id, @Valid @RequestBody CommentRequest request) {
        return commentService.create(me, id, request.text());
    }

    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser long me, @PathVariable long id) {
        commentService.delete(me, id);
    }
}
