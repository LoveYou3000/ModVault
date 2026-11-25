package com.modvault.common;

/**
 * Domain exception carrying an Anthropic error type. Thrown anywhere in the stack
 * and translated by the web layer into an Anthropic error body.
 */
public class ApiException extends RuntimeException {

    private final AnthropicErrorType errorType;

    public ApiException(AnthropicErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public ApiException(AnthropicErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public AnthropicErrorType errorType() {
        return errorType;
    }

    public int httpStatus() {
        return errorType.httpStatus();
    }

    public static ApiException badRequest(String message) {
        return new ApiException(AnthropicErrorType.INVALID_REQUEST_ERROR, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(AnthropicErrorType.AUTHENTICATION_ERROR, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(AnthropicErrorType.NOT_FOUND_ERROR, message);
    }

    public static ApiException apiError(String message) {
        return new ApiException(AnthropicErrorType.API_ERROR, message);
    }

    public static ApiException overloaded(String message) {
        return new ApiException(AnthropicErrorType.OVERLOADED_ERROR, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(AnthropicErrorType.CONFLICT, message);
    }
}
