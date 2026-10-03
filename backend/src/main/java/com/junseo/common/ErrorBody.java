package com.junseo.common;

import org.springframework.http.ResponseEntity;

public record ErrorBody(String code, String message) {

    public static ErrorBody of(ErrorCode code) {
        return new ErrorBody(code.name(), code.message());
    }

    public static ResponseEntity<ErrorBody> response(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(new ErrorBody(code.name(), message));
    }
}
