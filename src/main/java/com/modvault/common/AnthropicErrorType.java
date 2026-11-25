package com.modvault.common;

/**
 * Anthropic error types (client-facing error vocabulary) and their HTTP status codes.
 * See PRD v0.2 appendix A.4.
 */
public enum AnthropicErrorType {

    INVALID_REQUEST_ERROR("invalid_request_error", 400),
    AUTHENTICATION_ERROR("authentication_error", 401),
    PERMISSION_ERROR("permission_error", 403),
    NOT_FOUND_ERROR("not_found_error", 404),
    REQUEST_TOO_LARGE("request_too_large", 413),
    RATE_LIMIT_ERROR("rate_limit_error", 429),
    API_ERROR("api_error", 500),
    OVERLOADED_ERROR("overloaded_error", 529),
    /** Admin-plane only: conflicting resource state (not part of the Anthropic enum). */
    CONFLICT("conflict", 409);

    private final String wireValue;
    private final int httpStatus;

    AnthropicErrorType(String wireValue, int httpStatus) {
        this.wireValue = wireValue;
        this.httpStatus = httpStatus;
    }

    public String wireValue() {
        return wireValue;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
