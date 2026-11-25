package com.modvault.upstream;

/**
 * Raised when the upstream call fails at the transport layer (connect/read timeout,
 * DNS, connection reset) or when a 2xx response body cannot be parsed.
 */
public class UpstreamException extends RuntimeException {

    public UpstreamException(String message) {
        super(message);
    }

    public UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
