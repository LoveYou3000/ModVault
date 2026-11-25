package com.modvault.upstream;

/**
 * Raised when the upstream returns a non-2xx HTTP status. The body is retained for
 * error summarization but is never forwarded to the client verbatim.
 */
public class UpstreamHttpException extends RuntimeException {

    private final int statusCode;
    private final String body;

    public UpstreamHttpException(int statusCode, String body) {
        super("upstream returned HTTP " + statusCode);
        this.statusCode = statusCode;
        this.body = body;
    }

    public int statusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }
}
