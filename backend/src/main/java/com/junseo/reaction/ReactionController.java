package com.junseo.reaction;

import com.junseo.common.ApiException;
import com.junseo.common.ErrorCode;
import com.junseo.common.security.CurrentUser;
import com.junseo.moment.MomentView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/moments/{id}/reactions")
public class ReactionController {

    /** {@code count}: how many taps this request adds (the app sends a quick run of taps at once). Defaults to 1. */
    public record ReactionRequest(
            @NotNull(message = "이모지를 골라 주세요.") String emoji,
            @Min(value = 1, message = "한 번에 1~20번까지 보낼 수 있어요.")
            @Max(value = ReactionEmojis.MAX_TAPS_PER_REQUEST, message = "한 번에 1~20번까지 보낼 수 있어요.")
            Integer count) {}

    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @PostMapping
    MomentView react(@CurrentUser long me, @PathVariable long id, @Valid @RequestBody ReactionRequest request) {
        String emoji = ReactionEmojis.canonical(request.emoji());
        if (emoji == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이모지는 ❤️ 😂 😢 👍 🖕 중에서 골라 주세요.");
        }
        return reactionService.react(me, id, emoji, request.count() == null ? 1 : request.count());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@CurrentUser long me, @PathVariable long id) {
        reactionService.remove(me, id);
    }
}
