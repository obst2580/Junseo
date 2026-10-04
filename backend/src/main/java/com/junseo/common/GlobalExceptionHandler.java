package com.junseo.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handleApi(ApiException e) {
        return ErrorBody.response(e.code(), e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorBody> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse(ErrorCode.VALIDATION_FAILED.message());
        return ErrorBody.response(ErrorCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ErrorBody> handleMultipart(MultipartException e) {
        return ErrorBody.response(ErrorCode.VALIDATION_FAILED, "사진을 multipart 'image' 필드로 보내 주세요.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ErrorBody.response(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse(ErrorCode.VALIDATION_FAILED.message());
        return body(ErrorCode.VALIDATION_FAILED, message, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse(ErrorCode.VALIDATION_FAILED.message());
        return body(ErrorCode.VALIDATION_FAILED, message, headers);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return body(ErrorCode.INVALID_IMAGE, ErrorCode.INVALID_IMAGE.message(), headers);
    }

    /** Every other Spring MVC exception keeps its HTTP status but gets the contract's body. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorCode code = ErrorCode.forStatus(status.value());
        return ResponseEntity.status(status).headers(headers).body(new ErrorBody(code.name(), code.message()));
    }

    private static ResponseEntity<Object> body(ErrorCode code, String message, HttpHeaders headers) {
        return ResponseEntity.status(code.status()).headers(headers).body(new ErrorBody(code.name(), message));
    }
}
