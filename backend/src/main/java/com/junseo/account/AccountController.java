package com.junseo.account;

import com.junseo.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    public record DeleteAccountRequest(String password) {}

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    /** 계정 삭제 (앱 설정). POST, not DELETE with a body, so no proxy drops the password. */
    @PostMapping("/api/me/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser long me, @RequestBody DeleteAccountRequest request) {
        accounts.deleteOwn(me, request.password());
    }
}
