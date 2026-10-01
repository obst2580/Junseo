package com.junseo.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "10MB 이하의 JPEG 또는 PNG 사진만 올릴 수 있어요."),
    CANNOT_ADD_SELF(HttpStatus.BAD_REQUEST, "내 초대 코드로는 나를 추가할 수 없어요."),
    NOT_ALLOWED_ON_OWN_MOMENT(HttpStatus.BAD_REQUEST, "내 사진에는 할 수 없어요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요."),
    NOT_FRIENDS(HttpStatus.FORBIDDEN, "친구에게만 메시지를 보낼 수 있어요."),
    NOT_MUTUAL_FRIENDS(HttpStatus.FORBIDDEN, "서로 친구인 사람끼리만 단챗을 만들 수 있어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요."),
    INVITE_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "초대 코드를 찾을 수 없어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청이에요."),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    ALREADY_FRIENDS(HttpStatus.CONFLICT, "이미 친구예요."),
    FRIEND_LIMIT_REACHED(HttpStatus.CONFLICT, "친구는 20명까지 추가할 수 있어요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    /** Code for errors raised by the framework itself, where only the HTTP status is known. */
    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 413 -> INVALID_IMAGE;
            default -> status >= 500 ? INTERNAL_ERROR : VALIDATION_FAILED;
        };
    }
}
