package com.junseo.reaction;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.security.CurrentUser;
import com.junseo.moment.MomentView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

    public record ReactionRequest(@NotNull(message = "이모지를 골라 주세요.") String emoji) {}

    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @PutMapping
    MomentView react(@CurrentUser long me, @PathVariable long id, @Valid @RequestBody ReactionRequest request) {
        String emoji = ReactionEmojis.canonical(request.emoji());
        if (emoji == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이모지는 ❤️ 😂 😢 👍 🖕 중에서 골라 주세요.");
        }
        return reactionService.react(me, id, emoji);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@CurrentUser long me, @PathVariable long id) {
        reactionService.remove(me, id);
    }
}
