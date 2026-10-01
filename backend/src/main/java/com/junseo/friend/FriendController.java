package com.junseo.friend;

import com.junseo.common.security.CurrentUser;
import com.junseo.user.UserService;
import com.junseo.user.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    public record FriendsResponse(List<UserSummary> friends, int limit) {}

    public record AddFriendRequest(
            @NotBlank(message = "초대 코드를 입력해 주세요.") @Size(max = 64, message = "초대 코드가 올바르지 않아요.")
            String inviteCode) {}

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping
    FriendsResponse list(@CurrentUser long me) {
        return new FriendsResponse(friendService.list(me), UserService.FRIEND_LIMIT);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserSummary add(@CurrentUser long me, @Valid @RequestBody AddFriendRequest request) {
        return friendService.addByInviteCode(me, request.inviteCode());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfriend(@CurrentUser long me, @PathVariable long userId) {
        friendService.unfriend(me, userId);
    }
}
