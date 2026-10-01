package com.junseo.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Replaces Boot's whitelabel/JSON error page so container-level errors also use {@code {code, message}}. */
@RestController
public class JsonErrorController implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<ErrorBody> error(HttpServletRequest request) {
        int status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer s ? s : 500;
        ErrorCode code = ErrorCode.forStatus(status);
        return ResponseEntity.status(status).body(ErrorBody.of(code));
    }
}
