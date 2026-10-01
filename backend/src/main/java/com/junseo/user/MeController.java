package com.junseo.user;

import com.junseo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    public record UpdateMeRequest(
            @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 20, message = "이름은 1~20자로 입력해 주세요.")
            String displayName) {

        public UpdateMeRequest {
            displayName = displayName == null ? null : displayName.strip();
        }
    }

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    Me me(@CurrentUser long me) {
        return userService.me(me);
    }

    @PatchMapping
    Me update(@CurrentUser long me, @Valid @RequestBody UpdateMeRequest request) {
        return userService.rename(me, request.displayName());
    }

    @PostMapping("/invite-code")
    Me rotateInviteCode(@CurrentUser long me) {
        return userService.rotateInviteCode(me);
    }
}
