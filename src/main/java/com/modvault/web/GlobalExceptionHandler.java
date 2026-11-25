package com.modvault.web;

import com.modvault.common.AnthropicErrorType;
import com.modvault.common.ApiException;
import com.modvault.translate.model.AnthropicErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into the Anthropic error body (FR-05). Response bodies never
 * contain stack traces, SQL, or the upstream response body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<AnthropicErrorResponse> handleApiException(ApiException e) {
        return build(e.errorType(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AnthropicErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("malformed request body: {}", e.getMessage());
        return build(AnthropicErrorType.INVALID_REQUEST_ERROR,
                "invalid request body: JSON structure does not match the expected schema");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<AnthropicErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return build(AnthropicErrorType.INVALID_REQUEST_ERROR, "method not allowed");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AnthropicErrorResponse> handleGeneric(Exception e) {
        log.error("unhandled error", e);
        return build(AnthropicErrorType.API_ERROR, "internal server error");
    }

    private ResponseEntity<AnthropicErrorResponse> build(AnthropicErrorType type, String message) {
        AnthropicErrorResponse body = AnthropicErrorResponse.of(type.wireValue(), message);
        return ResponseEntity.status(HttpStatus.valueOf(type.httpStatus())).body(body);
    }
}
