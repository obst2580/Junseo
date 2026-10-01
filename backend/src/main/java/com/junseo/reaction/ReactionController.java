package com.junseo.reaction;

import com.junseo.common.security.CurrentUser;
import com.junseo.moment.MomentView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/moments/{id}/reaction")
public class ReactionController {

    /** Length is counted in UTF-16 units, the same as JavaScript's {@code string.length}. */
    public record ReactionRequest(
            @NotNull(message = "이모지를 골라 주세요.")
            @Size(min = 1, max = 16, message = "이모지는 1~16자여야 해요.")
            @Pattern(regexp = "(?U)\\S+", message = "이모지에 공백을 넣을 수 없어요.")
            String emoji) {}

    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @PutMapping
    MomentView react(@CurrentUser long me, @PathVariable long id, @Valid @RequestBody ReactionRequest request) {
        return reactionService.react(me, id, request.emoji());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@CurrentUser long me, @PathVariable long id) {
        reactionService.remove(me, id);
    }
}
