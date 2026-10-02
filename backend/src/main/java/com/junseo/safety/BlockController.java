package com.junseo.safety;

import com.junseo.common.security.CurrentUser;
import com.junseo.user.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlockController {

    public record BlockRequest(@NotNull(message = "누구를 차단할지 알려 주세요.") Long userId) {}

    public record BlockList(List<UserSummary> items) {}

    private final BlockService blocks;

    public BlockController(BlockService blocks) {
        this.blocks = blocks;
    }

    /** 내가 차단한 사람 (최근 순) */
    @GetMapping("/api/blocks")
    BlockList list(@CurrentUser long me) {
        return new BlockList(blocks.list(me));
    }

    @PostMapping("/api/blocks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void block(@CurrentUser long me, @Valid @RequestBody BlockRequest request) {
        blocks.block(me, request.userId());
    }

    /** 차단 풀기. 친구는 다시 맺어야 한다 (초대 코드). */
    @DeleteMapping("/api/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unblock(@CurrentUser long me, @PathVariable long userId) {
        blocks.unblock(me, userId);
    }
}
